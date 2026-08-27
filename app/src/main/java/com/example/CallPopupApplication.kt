package com.example

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Process
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.repository.CallRepository
import com.example.service.CallMonitorWatchdogWorker
import com.example.websocket.CallWebSocketClient
import java.util.concurrent.TimeUnit

class CallPopupApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "call_crm_monitor_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Call Monitor & CRM Service"
        lateinit var instance: CallPopupApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannel()

        // Eager initialize repository and websocket
        CallRepository.getInstance(this)

        // FloatingWindowOverlayService runs in its own ":overlay" process (see
        // AndroidManifest.xml), which means Application.onCreate() runs again there too.
        // WorkManager's auto-init ContentProvider only runs in the main process, so calling
        // WorkManager.getInstance() from the :overlay process crashes it immediately with
        // "WorkManager is not initialized properly". The watchdog only needs to run once.
        if (isMainProcess()) {
            scheduleCallMonitorWatchdog()
        }
    }

    private fun isMainProcess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName() == packageName
        }
        val activityManager = getSystemService(ActivityManager::class.java) ?: return true
        val myPid = Process.myPid()
        return activityManager.runningAppProcesses.orEmpty().any { it.pid == myPid && it.processName == packageName }
    }

    private fun scheduleCallMonitorWatchdog() {
        // 15 minutes is WorkManager's floor for periodic work - this is a backstop restart
        // check, not the primary keep-alive mechanism (that's the foreground service itself
        // plus START_STICKY and BootReceiver).
        val request = PeriodicWorkRequestBuilder<CallMonitorWatchdogWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.NONE)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CallMonitorWatchdogWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies when background call monitor and WebSocket sync are active"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
