package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.repository.CallRepository
import com.example.websocket.CallWebSocketClient

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
