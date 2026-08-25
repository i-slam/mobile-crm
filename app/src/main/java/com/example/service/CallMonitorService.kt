package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.CallPopupApplication
import com.example.MainActivity
import com.example.data.repository.CallRepository
import com.example.overlay.CallOverlayActivity
import com.example.receiver.CallStateReceiver
import com.example.util.CallLogHelper
import com.example.websocket.CallWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CallMonitorService : Service() {

    private var callStateReceiver: CallStateReceiver? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var inServiceCallStartTime: Long = 0
    private var inServiceLastState: Int = TelephonyManager.CALL_STATE_IDLE
    private var inServiceIncomingNumber: String? = null
    private var inServiceIsIncoming: Boolean = false

    companion object {
        private const val TAG = "CallMonitorService"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.service.START"
        const val ACTION_STOP = "com.example.service.STOP"

        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerCallReceiver()
        registerTelephonyListener()
        registerNetworkMonitoring()

        // Ensure WebSocket is connected
        val repo = CallRepository.getInstance(this)
        val settings = repo.settings.value
        if (settings.wsServerUrl.isNotBlank()) {
            CallWebSocketClient.getInstance().connect(settings.wsServerUrl, settings.wsAuthToken)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            isRunning = false
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        isRunning = true

        return START_STICKY
    }

    private fun registerTelephonyListener() {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallStateTransition(state, null)
                    }
                }
                telephonyManager.registerTelephonyCallback(mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallStateTransition(state, phoneNumber)
                    }
                }
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register TelephonyCallback: ${e.message}")
        }
    }

    private fun handleCallStateTransition(state: Int, incomingNumber: String?) {
        if (!incomingNumber.isNullOrBlank()) {
            inServiceIncomingNumber = incomingNumber
        }

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                inServiceIsIncoming = true
                inServiceCallStartTime = System.currentTimeMillis()
                inServiceLastState = TelephonyManager.CALL_STATE_RINGING
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (inServiceLastState != TelephonyManager.CALL_STATE_RINGING) {
                    inServiceIsIncoming = false
                }
                inServiceCallStartTime = System.currentTimeMillis()
                inServiceLastState = TelephonyManager.CALL_STATE_OFFHOOK
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (inServiceLastState == TelephonyManager.CALL_STATE_RINGING || inServiceLastState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    val endTime = System.currentTimeMillis()
                    val duration = if (inServiceCallStartTime > 0) {
                        ((endTime - inServiceCallStartTime) / 1000).toInt().coerceAtLeast(1)
                    } else 0

                    val callType = if (inServiceIsIncoming) {
                        if (inServiceLastState == TelephonyManager.CALL_STATE_RINGING) "MISSED" else "INCOMING"
                    } else "OUTGOING"

                    var targetNumber = inServiceIncomingNumber
                    var finalDuration = duration

                    val recentCall = CallLogHelper.getMostRecentCall(this)
                    if (recentCall != null) {
                        if (targetNumber.isNullOrBlank() && recentCall.number.isNotBlank()) {
                            targetNumber = recentCall.number
                        }
                        if (recentCall.durationSeconds > 0 && finalDuration == 0) {
                            finalDuration = recentCall.durationSeconds
                        }
                    }

                    val resolvedNumber = if (!targetNumber.isNullOrBlank()) targetNumber else "Unknown Number"
                    val startTime = inServiceCallStartTime.takeIf { it > 0 } ?: (endTime - (finalDuration * 1000L))

                    val settings = CallRepository.getInstance(this).settings.value
                    if (settings.overlayEnabled) {
                        CallOverlayActivity.launch(
                            context = this,
                            phoneNumber = resolvedNumber,
                            durationSeconds = finalDuration,
                            callType = callType,
                            startTime = startTime,
                            endTime = endTime
                        )
                    }
                }
                inServiceLastState = TelephonyManager.CALL_STATE_IDLE
                inServiceCallStartTime = 0
                inServiceIncomingNumber = null
                inServiceIsIncoming = false
            }
        }
    }

    private fun registerNetworkMonitoring() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network became available, reconnecting WS & flushing queue")
                    val repo = CallRepository.getInstance(applicationContext)
                    val settings = repo.settings.value
                    if (settings.wsServerUrl.isNotBlank()) {
                        CallWebSocketClient.getInstance().connect(settings.wsServerUrl, settings.wsAuthToken)
                    }
                    scope.launch {
                        repo.flushPendingQueue()
                    }
                }
            }
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register NetworkCallback: ${e.message}")
        }
    }

    private fun registerCallReceiver() {
        if (callStateReceiver == null) {
            callStateReceiver = CallStateReceiver()
            val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            registerReceiver(callStateReceiver, filter)
        }
    }

    private fun unregisterCallReceiver() {
        callStateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // ignore
            }
            callStateReceiver = null
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CallPopupApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Call Notes & Quick Share Active")
            .setContentText("Monitoring ended calls for instant showroom dispatch & CRM sync")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        unregisterCallReceiver()
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // ignore
            }
        }
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

