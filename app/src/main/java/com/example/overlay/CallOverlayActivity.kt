package com.example.overlay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MyApplicationTheme

class CallOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_END_TIME = "extra_end_time"

        fun launch(
            context: Context,
            phoneNumber: String,
            durationSeconds: Int = 0,
            callType: String = "INCOMING",
            startTime: Long = System.currentTimeMillis(),
            endTime: Long = System.currentTimeMillis()
        ) {
            val intent = Intent(context, CallOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
                putExtra(EXTRA_CALL_TYPE, callType)
                putExtra(EXTRA_START_TIME, startTime)
                putExtra(EXTRA_END_TIME, endTime)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "+1 (555) 019-2834"
        val durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 42)
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "INCOMING"
        val startTime = intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis() - (durationSeconds * 1000L))
        val endTime = intent.getLongExtra(EXTRA_END_TIME, System.currentTimeMillis())

        setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CallOverlayDialogContent(
                        phoneNumber = phoneNumber,
                        durationSeconds = durationSeconds,
                        callType = callType,
                        startTime = startTime,
                        endTime = endTime,
                        onDismiss = {
                            finish()
                        }
                    )
                }
            }
        }
    }
}
