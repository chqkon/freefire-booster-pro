package com.example.freefireboosterpro.game

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.freefireboosterpro.data.GameModeSnapshot
import com.example.freefireboosterpro.data.SystemOptimizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Restaura el modo juego al detectar que la app objetivo dejó el primer plano. */
class GameModeMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var optimizer: SystemOptimizer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var targetPackage: String? = null
    private var snapshot: GameModeSnapshot? = null
    private var armedAt = 0L

    private val monitor = object : Runnable {
        override fun run() {
            val target = targetPackage
            if (System.currentTimeMillis() - armedAt < ARM_DELAY_MS) {
                handler.postDelayed(this, POLL_INTERVAL_MS)
            } else if (target != null && hasUsageAccess() && foregroundPackage() != target) {
                serviceScope.launch {
                    optimizer.setGameMode(false, snapshot)
                    stopSelf()
                }
            } else {
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        optimizer = SystemOptimizer(this)
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        targetPackage = intent?.getStringExtra(EXTRA_TARGET)
        armedAt = System.currentTimeMillis()
        snapshot = intent?.let {
            GameModeSnapshot(
                it.getFloatExtra(EXTRA_WINDOW, 1f),
                it.getFloatExtra(EXTRA_TRANSITION, 1f),
                it.getFloatExtra(EXTRA_ANIMATOR, 1f)
            )
        }
        handler.removeCallbacks(monitor)
        handler.post(monitor)
        return START_NOT_STICKY
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java)
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun foregroundPackage(): String? {
        val manager = getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = manager.queryEvents(now - 3_000L, now)
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                latest = event.packageName
            }
        }
        return latest
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("FreeFire Booster Pro")
            .setContentText("Modo juego activo; restauración automática habilitada")
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Modo juego", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TARGET = "target_package"
        const val EXTRA_WINDOW = "window_scale"
        const val EXTRA_TRANSITION = "transition_scale"
        const val EXTRA_ANIMATOR = "animator_scale"
        private const val CHANNEL_ID = "game_mode"
        private const val NOTIFICATION_ID = 4102
        private const val POLL_INTERVAL_MS = 1_000L
        private const val ARM_DELAY_MS = 3_000L
    }
}
