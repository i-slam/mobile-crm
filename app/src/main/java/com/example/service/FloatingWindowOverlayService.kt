package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.overlay.CallOverlayDialogContent
import com.example.ui.theme.MyApplicationTheme

class FloatingWindowOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    companion object {
        private const val TAG = "FloatingWindowOverlay"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"
        const val EXTRA_CALL_TYPE = "extra_call_type"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_END_TIME = "extra_end_time"

        fun show(
            context: Context,
            phoneNumber: String,
            durationSeconds: Int,
            callType: String,
            startTime: Long,
            endTime: Long
        ) {
            val intent = Intent(context, FloatingWindowOverlayService::class.java).apply {
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
                putExtra(EXTRA_CALL_TYPE, callType)
                putExtra(EXTRA_START_TIME, startTime)
                putExtra(EXTRA_END_TIME, endTime)
            }
            try {
                val result = context.startService(intent)
                Log.i(TAG, "show(): startService returned $result for number=$phoneNumber")
            } catch (e: Exception) {
                Log.e(TAG, "show(): startService threw", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val phoneNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER) ?: "+1 (555) 019-2834"
        val durationSeconds = intent?.getIntExtra(EXTRA_DURATION_SECONDS, 45) ?: 45
        val callType = intent?.getStringExtra(EXTRA_CALL_TYPE) ?: "INCOMING"
        val startTime = intent?.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis() - 45000L) ?: System.currentTimeMillis()
        val endTime = intent?.getLongExtra(EXTRA_END_TIME, System.currentTimeMillis()) ?: System.currentTimeMillis()

        Log.i(TAG, "onStartCommand: number=$phoneNumber callType=$callType canDrawOverlays=${Settings.canDrawOverlays(this)}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // Permission revoked, stop self
            Log.w(TAG, "onStartCommand: overlay permission not granted, stopping self")
            stopSelf()
            return START_NOT_STICKY
        }

        // NOTE: addView() must run on the main thread - Compose's ComposeView internally calls
        // LifecycleRegistry.addObserver() (via WindowRecomposer) as part of onAttachedToWindow(),
        // and that call unconditionally throws IllegalStateException off the main thread (tried
        // moving this to a background thread; it broke the overlay outright rather than avoiding
        // the hang). A one-off hang was observed here during rapid repeated test triggers earlier
        // in development; if it recurs reliably, the real fix is running this Service in its own
        // process (android:process) rather than fighting Compose's main-thread requirement.
        showOverlay(phoneNumber, durationSeconds, callType, startTime, endTime)
        return START_NOT_STICKY
    }

    private fun showOverlay(
        phoneNumber: String,
        durationSeconds: Int,
        callType: String,
        startTime: Long,
        endTime: Long
    ) {
        removeOverlay()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowOverlayService)
            setContent {
                MyApplicationTheme(darkTheme = false) {
                    CallOverlayDialogContent(
                        phoneNumber = phoneNumber,
                        durationSeconds = durationSeconds,
                        callType = callType,
                        startTime = startTime,
                        endTime = endTime,
                        onDismiss = {
                            removeOverlay()
                            stopSelf()
                        }
                    )
                }
            }
        }

        try {
            windowManager?.addView(composeView, params)
            overlayView = composeView
            Log.i(TAG, "showOverlay: addView succeeded")
        } catch (e: Exception) {
            Log.e(TAG, "showOverlay: addView failed", e)
            stopSelf()
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // ignore
            }
            overlayView = null
        }
    }

    override fun onDestroy() {
        removeOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
