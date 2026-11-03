package com.example.freefireboosterpro.overlay

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.freefireboosterpro.R
import kotlin.math.roundToInt

/**
 * HUD opcional. No toca Free Fire, no usa AccessibilityService y no requiere root.
 * El FPS es una estimación del ritmo de frames del dispositivo, no una lectura
 * privilegiada de SurfaceFlinger.
 */
class FpsOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var hudView: HudView
    private val handler = Handler(Looper.getMainLooper())
    private val choreographer by lazy { Choreographer.getInstance() }
    private var frameCount = 0
    private var lastSampleMs = 0L
    private var batteryTempC = 0f
    private var fps = 0
    private var frameCallback: Choreographer.FrameCallback? = null

    private val telemetryRunnable = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - lastSampleMs
            if (elapsed > 0) {
                fps = ((frameCount * 1000f) / elapsed).roundToInt().coerceIn(0, 240)
            }
            frameCount = 0
            lastSampleMs = now
            batteryTempC = readBatteryTemperature()
            hudView.update(fps, batteryTempC, availableMemoryMb())
            handler.postDelayed(this, TELEMETRY_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        windowManager = getSystemService(WindowManager::class.java)
        hudView = HudView(this) { x, y -> updatePosition(x, y) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = 18
            y = 110
        }

        try {
            windowManager.addView(hudView, params)
            running = true
            lastSampleMs = SystemClock.elapsedRealtime()
            startFrameSampling()
            handler.post(telemetryRunnable)
        } catch (_: RuntimeException) {
            stopSelf()
        }
    }

    private fun startFrameSampling() {
        val callback = Choreographer.FrameCallback {
            frameCount++
            choreographer.postFrameCallback(frameCallback)
        }
        frameCallback = callback
        choreographer.postFrameCallback(callback)
    }

    private fun updatePosition(x: Int, y: Int) {
        if (!::hudView.isInitialized || !hudView.isAttachedToWindow) return
        val params = hudView.layoutParams as? WindowManager.LayoutParams ?: return
        params.x = x
        params.y = y
        runCatching { windowManager.updateViewLayout(hudView, params) }
    }

    private fun readBatteryTemperature(): Float {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (intent?.getIntExtra("temperature", 0) ?: 0) / 10f
    }

    private fun availableMemoryMb(): Long {
        val info = ActivityManager.MemoryInfo()
        getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return info.availMem / BYTES_PER_MB
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("FreeFire Booster Pro")
            .setContentText("HUD de rendimiento activo")
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "HUD de rendimiento",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        frameCallback?.let(choreographer::removeFrameCallback)
        frameCallback = null
        handler.removeCallbacksAndMessages(null)
        if (::hudView.isInitialized && hudView.isAttachedToWindow) {
            runCatching { windowManager.removeView(hudView) }
        }
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private class HudView(
        context: Context,
        private val onMove: (Int, Int) -> Unit
    ) : View(context) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 15, 15, 18) }
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f * resources.displayMetrics.scaledDensity
            isFakeBoldText = true
        }
        private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 230, 255)
            textSize = 13f * resources.displayMetrics.scaledDensity
            isFakeBoldText = true
        }
        private val rect = RectF()
        private var fps = 0
        private var temp = 0f
        private var memory = 0L
        private var downX = 0f
        private var downY = 0f
        private var initialX = 0
        private var initialY = 0

        init {
            setPadding(18, 12, 18, 12)
            setBackgroundColor(Color.TRANSPARENT)
        }

        fun update(fps: Int, temperature: Float, availableMemory: Long) {
            this.fps = fps
            temp = temperature
            memory = availableMemory
            postInvalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(dp(190), dp(74))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rect, dp(12).toFloat(), dp(12).toFloat(), backgroundPaint)
            canvas.drawText("FREEFIRE BOOSTER", dp(12).toFloat(), dp(22).toFloat(), titlePaint)
            canvas.drawText("FPS ${if (fps > 0) fps else "--"}", dp(12).toFloat(), dp(48).toFloat(), valuePaint)
            canvas.drawText("BAT ${if (temp > 0f) "%.1f°C".format(temp) else "--"}", dp(70).toFloat(), dp(48).toFloat(), valuePaint)
            canvas.drawText("RAM ${memory}M", dp(135).toFloat(), dp(48).toFloat(), valuePaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    val params = layoutParams as? WindowManager.LayoutParams
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    onMove(
                        initialX + (event.rawX - downX).roundToInt(),
                        initialY + (event.rawY - downY).roundToInt()
                    )
                    return true
                }
            }
            return true
        }

        private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    }

    companion object {
        private const val CHANNEL_ID = "performance_hud"
        private const val NOTIFICATION_ID = 4101
        private const val TELEMETRY_INTERVAL_MS = 1_000L
        private const val BYTES_PER_MB = 1_048_576L
        @Volatile private var running = false

        fun isRunning(): Boolean = running
    }
}
