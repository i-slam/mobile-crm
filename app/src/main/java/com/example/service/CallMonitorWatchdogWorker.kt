package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.CallRepository
import com.example.util.PermissionUtils

/**
 * Periodic self-healing check: if call monitoring is supposed to be on but the service isn't
 * actually running (killed by the OS, an OEM battery manager, low memory, etc.), restart it.
 * START_STICKY and BootReceiver cover most restart scenarios already; this is the backstop
 * for everything else, since WorkManager's own scheduling has broader OS cooperation than a
 * plain Service does.
 */
class CallMonitorWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = CallRepository.getInstance(applicationContext).settings.value
        val hasCriticalPerms = PermissionUtils.areCriticalCallPermissionsGranted(applicationContext)

        if (settings.overlayEnabled && hasCriticalPerms && !CallMonitorService.isRunning) {
            Log.i(TAG, "Watchdog: CallMonitorService not running, restarting it")
            CallMonitorService.start(applicationContext)
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "CallMonitorWatchdog"
        const val WORK_NAME = "call_monitor_watchdog"
    }
}
