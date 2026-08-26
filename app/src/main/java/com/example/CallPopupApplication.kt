package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
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

        scheduleCallMonitorWatchdog()
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
