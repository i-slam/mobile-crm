package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.repository.CallRepository
import com.example.service.CallMonitorService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.d(TAG, "Device rebooted or package replaced: starting CallMonitorService")
            val repository = CallRepository.getInstance(context)
            val settings = repository.settings.value
            if (settings.overlayEnabled) {
                CallMonitorService.start(context)
            }
        }
    }
}
