package com.example.freefireboosterpro.overlay

import android.content.Context

data class OverlayConfig(
    val opacity: Float = 0.88f,
    val scale: Float = 1f,
    val showFps: Boolean = true,
    val showTemperature: Boolean = true,
    val showMemory: Boolean = true
)

object OverlayPreferences {
    private const val FILE = "hud_preferences"
    private const val OPACITY = "opacity"
    private const val SCALE = "scale"
    private const val SHOW_FPS = "show_fps"
    private const val SHOW_TEMP = "show_temperature"
    private const val SHOW_MEMORY = "show_memory"

    fun read(context: Context): OverlayConfig {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return OverlayConfig(
            opacity = prefs.getFloat(OPACITY, 0.88f),
            scale = prefs.getFloat(SCALE, 1f),
            showFps = prefs.getBoolean(SHOW_FPS, true),
            showTemperature = prefs.getBoolean(SHOW_TEMP, true),
            showMemory = prefs.getBoolean(SHOW_MEMORY, true)
        )
    }

    fun write(context: Context, config: OverlayConfig) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putFloat(OPACITY, config.opacity.coerceIn(0.35f, 1f))
            .putFloat(SCALE, config.scale.coerceIn(0.75f, 1.5f))
            .putBoolean(SHOW_FPS, config.showFps)
            .putBoolean(SHOW_TEMP, config.showTemperature)
            .putBoolean(SHOW_MEMORY, config.showMemory)
            .apply()
    }
}
