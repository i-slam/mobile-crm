package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.example.data.repository.CallRepository
import com.example.overlay.CallOverlayActivity
import com.example.service.FloatingWindowOverlayService

class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"

        private var lastState: String? = null
        private var callStartTime: Long = 0
        private var savedNumber: String? = null
        private var isIncoming: Boolean = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (!incomingNumber.isNullOrBlank()) {
            savedNumber = incomingNumber
        }

        Log.d(TAG, "PhoneState changed: $stateStr, number: $savedNumber")

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                isIncoming = true
                callStartTime = System.currentTimeMillis()
                lastState = TelephonyManager.EXTRA_STATE_RINGING
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (lastState != TelephonyManager.EXTRA_STATE_RINGING) {
                    isIncoming = false
                }
                callStartTime = System.currentTimeMillis()
                lastState = TelephonyManager.EXTRA_STATE_OFFHOOK
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (lastState == TelephonyManager.EXTRA_STATE_RINGING || lastState == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    // Call ended
                    val endTime = System.currentTimeMillis()
                    val durationSeconds = if (callStartTime > 0) {
                        ((endTime - callStartTime) / 1000).toInt().coerceAtLeast(1)
                    } else {
                        0
                    }

                    val callType = if (isIncoming) {
                        if (lastState == TelephonyManager.EXTRA_STATE_RINGING) "MISSED" else "INCOMING"
                    } else {
                        "OUTGOING"
                    }

                    var targetNumber = savedNumber
                    var finalCallType = callType
                    var finalDuration = durationSeconds
                    var finalStartTime = callStartTime.takeIf { it > 0 } ?: (endTime - (durationSeconds * 1000L))

                    // If number wasn't in broadcast extra (standard on modern Android), query CallLog
                    val recentCall = com.example.util.CallLogHelper.getMostRecentCall(context)
                    if (recentCall != null) {
                        if (targetNumber.isNullOrBlank() && recentCall.number.isNotBlank()) {
                            targetNumber = recentCall.number
                        }
                        if (recentCall.durationSeconds > 0 && finalDuration == 0) {
                            finalDuration = recentCall.durationSeconds
                        }
                        if (recentCall.callType.isNotBlank()) {
                            finalCallType = recentCall.callType
                        }
                    }

                    val resolvedNumber = if (!targetNumber.isNullOrBlank()) targetNumber else "Unknown Number"

                    Log.i(TAG, "Call finished: Type=$finalCallType, Number=$resolvedNumber, Duration=${finalDuration}s")

                    triggerOverlay(
                        context = context,
                        phoneNumber = resolvedNumber,
                        durationSeconds = finalDuration,
                        callType = finalCallType,
                        startTime = finalStartTime,
                        endTime = endTime
                    )
                }

                lastState = TelephonyManager.EXTRA_STATE_IDLE
                callStartTime = 0
                savedNumber = null
                isIncoming = false
            }
        }
    }

    private fun triggerOverlay(
        context: Context,
        phoneNumber: String,
        durationSeconds: Int,
        callType: String,
        startTime: Long,
        endTime: Long
    ) {
        val repository = CallRepository.getInstance(context)
        val settings = repository.settings.value
        if (!settings.overlayEnabled) {
            Log.d(TAG, "Overlay is disabled in settings")
            return
        }

        val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        if (canDrawOverlays) {
            // Launch floating window service or translucent activity
            CallOverlayActivity.launch(
                context = context,
                phoneNumber = phoneNumber,
                durationSeconds = durationSeconds,
                callType = callType,
                startTime = startTime,
                endTime = endTime
            )
        } else {
            // Launch translucent activity as reliable fallback
            CallOverlayActivity.launch(
                context = context,
                phoneNumber = phoneNumber,
                durationSeconds = durationSeconds,
                callType = callType,
                startTime = startTime,
                endTime = endTime
            )
        }
    }
}
