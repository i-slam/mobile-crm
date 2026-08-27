package com.example.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.CallLog
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.CallPopupApplication
import com.example.MainActivity
import com.example.data.repository.CallRepository
import com.example.overlay.CallOverlayActivity
import com.example.util.CallLogHelper
import com.example.util.PermissionUtils
import com.example.websocket.CallWebSocketClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class CallMonitorService : Service() {

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var callLogObserver: ContentObserver? = null

    // SupervisorJob, not a plain Job: this scope stays alive for the whole service lifetime and
    // launches many independent coroutines (popup resolution, CallLog checks, WS reconnects). A
    // plain Job would let one uncaught exception in any of them cancel the shared parent job,
    // silently turning every future scope.launch{} into a no-op with no crash and no log line -
    // exactly the kind of failure that's next to impossible to diagnose after the fact.
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Unhandled exception in CallMonitorService coroutine", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler)

    // Telephony callback + CallLog observer dispatch on a dedicated thread rather than main -
    // this app's main thread has been observed to hang (a WindowManager overlay call blocking
    // indefinitely on some OEM builds, see FloatingWindowOverlayService), and that must not be
    // able to take call detection down with it.
    private val detectionThread = HandlerThread("CallDetectionThread").apply { start() }
    private val detectionHandler = Handler(detectionThread.looper)
    private val detectionExecutor = Executor { runnable -> detectionHandler.post(runnable) }

    private var inServiceCallStartTime: Long = 0
    private var inServiceLastState: Int = TelephonyManager.CALL_STATE_IDLE
    private var inServiceIncomingNumber: String? = null
    private var inServiceIsIncoming: Boolean = false

    // Shared across both detection paths below, to dedupe when they both catch the same call.
    private var lastHandledCallLogTimestamp: Long = System.currentTimeMillis()
    private var lastShownNumber: String? = null
    private var lastShownAtMillis: Long = 0

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
        registerTelephonyListener()
        registerNetworkMonitoring()
        registerCallLogObserver()

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

    private fun registerTelephonyListener(attempt: Int = 1) {
        // Registered (and, for the pre-API-31 listen() path, dispatched) from detectionHandler's
        // thread rather than main - see the comment on detectionThread above.
        detectionHandler.post {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager == null) {
                Log.w(TAG, "registerTelephonyListener: no TelephonyManager")
                return@post
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            handleCallStateTransition(state, null)
                        }
                    }
                    telephonyManager.registerTelephonyCallback(detectionExecutor, callback)
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
                Log.i(TAG, "Telephony listener registered (attempt $attempt)")
            } catch (e: Exception) {
                Log.w(TAG, "Could not register TelephonyCallback (attempt $attempt): ${e.message}")
                // Call detection is the whole point of this service - retry a couple of times
                // rather than silently running with no listener for the rest of its lifetime.
                if (attempt < 3) {
                    detectionHandler.postDelayed({ registerTelephonyListener(attempt + 1) }, 2000L * attempt)
                }
            }
        }
    }

    private fun handleCallStateTransition(state: Int, incomingNumber: String?) {
        val stateName = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> "RINGING"
            TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
            TelephonyManager.CALL_STATE_IDLE -> "IDLE"
            else -> "UNKNOWN($state)"
        }
        Log.i(TAG, "handleCallStateTransition: state=$stateName incomingNumber=$incomingNumber prevState=$inServiceLastState")

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

                    Log.i(TAG, "IDLE after $inServiceLastState -> callType=$callType number=$inServiceIncomingNumber")
                    resolveAndShowPopup(
                        incomingNumber = inServiceIncomingNumber,
                        callStartTime = inServiceCallStartTime,
                        endTime = endTime,
                        duration = duration,
                        callType = callType
                    )
                }
                inServiceLastState = TelephonyManager.CALL_STATE_IDLE
                inServiceCallStartTime = 0
                inServiceIncomingNumber = null
                inServiceIsIncoming = false
            }
        }
    }

    private fun resolveAndShowPopup(
        incomingNumber: String?,
        callStartTime: Long,
        endTime: Long,
        duration: Int,
        callType: String
    ) {
        scope.launch {
            var targetNumber = incomingNumber
            var finalDuration = duration

            // MISSED calls in particular: the system can write the call-log entry a beat
            // after the telephony state already reports IDLE, so retry briefly rather than
            // querying once and potentially getting nothing (or a stale earlier call).
            val sinceMillis = callStartTime.takeIf { it > 0 } ?: endTime
            val recentCall = CallLogHelper.getMostRecentCallWithRetry(this@CallMonitorService, sinceMillis)
            if (recentCall != null) {
                if (targetNumber.isNullOrBlank() && recentCall.number.isNotBlank()) {
                    targetNumber = recentCall.number
                }
                if (recentCall.durationSeconds > 0 && finalDuration == 0) {
                    finalDuration = recentCall.durationSeconds
                }
                if (recentCall.timestampMillis > lastHandledCallLogTimestamp) {
                    lastHandledCallLogTimestamp = recentCall.timestampMillis
                }
            }

            val resolvedNumber = if (!targetNumber.isNullOrBlank()) targetNumber else "Unknown Number"
            val startTime = callStartTime.takeIf { it > 0 } ?: (endTime - (finalDuration * 1000L))

            Log.i(TAG, "resolveAndShowPopup: resolvedNumber=$resolvedNumber callType=$callType duration=$finalDuration recentCallFromLog=$recentCall")
            showPopupForCall(resolvedNumber, finalDuration, callType, startTime, endTime)
        }
    }

    /**
     * Second, independent detection path: a call that's rejected/screened below the telephony
     * API (carrier-side rejection, Do Not Disturb, some OEM call-screening) never surfaces a
     * RINGING state to this app at all, so handleCallStateTransition() never sees it - the only
     * signal is a new MISSED row appearing in the call log. Watching the log directly catches
     * those. showPopupForCall()'s dedup guard keeps this from double-firing on calls the
     * telephony path already caught.
     */
    private fun registerCallLogObserver() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        try {
            val observer = object : ContentObserver(detectionHandler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    checkForNewMissedCall()
                }
            }
            contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
            callLogObserver = observer
            Log.i(TAG, "CallLog observer registered")
        } catch (e: Exception) {
            Log.w(TAG, "Could not register CallLog observer: ${e.message}")
        }
    }

    private fun checkForNewMissedCall() {
        scope.launch {
            // Give the content resolver a moment to settle after the change notification.
            delay(400)
            val recent = CallLogHelper.getMostRecentCall(this@CallMonitorService)
            Log.i(TAG, "checkForNewMissedCall: recent=$recent lastHandledCallLogTimestamp=$lastHandledCallLogTimestamp")
            if (recent == null) return@launch
            if (recent.callType != "MISSED") return@launch
            if (recent.timestampMillis <= lastHandledCallLogTimestamp) return@launch
            lastHandledCallLogTimestamp = recent.timestampMillis

            val number = recent.number.ifBlank { "Unknown Number" }
            showPopupForCall(number, 0, "MISSED", recent.timestampMillis, recent.timestampMillis)
        }
    }

    private fun showPopupForCall(
        phoneNumber: String,
        durationSeconds: Int,
        callType: String,
        startTime: Long,
        endTime: Long
    ) {
        // Dedup guard: the telephony-state path and the CallLog observer path can both catch
        // the same call. Suppress a second trigger for the same number within a short window
        // rather than showing the popup twice.
        val now = System.currentTimeMillis()
        if (phoneNumber == lastShownNumber && (now - lastShownAtMillis) < 8000) {
            Log.d(TAG, "Suppressing duplicate popup trigger for $phoneNumber")
            return
        }
        lastShownNumber = phoneNumber
        lastShownAtMillis = now

        val settings = CallRepository.getInstance(this).settings.value
        val overlayGranted = PermissionUtils.isOverlayPermissionGranted(this)
        Log.i(TAG, "showPopupForCall: number=$phoneNumber callType=$callType overlayEnabled=${settings.overlayEnabled} overlayPermissionGranted=$overlayGranted")
        if (!settings.overlayEnabled) return

        if (overlayGranted) {
            // Reliable path: a TYPE_APPLICATION_OVERLAY window is exempt from the
            // Android 10+ background-activity-start restriction that would otherwise
            // silently block CallOverlayActivity from launching out of this service.
            FloatingWindowOverlayService.show(
                context = this,
                phoneNumber = phoneNumber,
                durationSeconds = durationSeconds,
                callType = callType,
                startTime = startTime,
                endTime = endTime
            )
        } else {
            // Best-effort fallback: without overlay permission this Activity launch
            // is likely to be blocked by the OS while the app is backgrounded.
            CallOverlayActivity.launch(
                context = this,
                phoneNumber = phoneNumber,
                durationSeconds = durationSeconds,
                callType = callType,
                startTime = startTime,
                endTime = endTime
            )
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
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // ignore
            }
        }
        callLogObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                // ignore
            }
        }
        detectionThread.quitSafely()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

