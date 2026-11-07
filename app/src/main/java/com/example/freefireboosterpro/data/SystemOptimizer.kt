package com.example.freefireboosterpro.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.example.freefireboosterpro.shizuku.ShizukuAccess
import com.example.freefireboosterpro.shizuku.ShizukuCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface OptimizerResult {
    data class Success(val message: String) : OptimizerResult
    data class Failure(val message: String) : OptimizerResult
}

/** Operaciones del sistema aisladas de Compose y ejecutadas sin Runtime.exec. */
class SystemOptimizer(context: Context) {
    private val appContext = context.applicationContext

    fun hasWriteSecureSettings(): Boolean =
        appContext.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun canUseShizuku(): Boolean = ShizukuAccess.status().permissionGranted

    suspend fun applyDensity(dpi: Int): OptimizerResult = withContext(Dispatchers.IO) {
        if (dpi !in DPI_RANGE) {
            return@withContext OptimizerResult.Failure("El DPI debe estar entre 360 y 650")
        }
        runShizukuCommand("wm", "density", dpi.toString()).toUserResult(
            success = "Densidad aplicada: $dpi dpi",
            failure = "No se pudo aplicar DPI; autoriza Shizuku primero"
        )
    }

    suspend fun setAnimationScales(instant: Boolean): OptimizerResult = withContext(Dispatchers.IO) {
        val scale = if (instant) "0.0" else "0.5"
        val shizuku = ShizukuAccess.status()
        if (shizuku.permissionGranted) {
            val keys = listOf(
                Settings.Global.WINDOW_ANIMATION_SCALE,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                Settings.Global.ANIMATOR_DURATION_SCALE
            )
            for (key in keys) {
                val result = runShizukuCommand("settings", "put", "global", key, scale)
                if (result.exitCode != 0) {
                    return@withContext OptimizerResult.Failure("Shizuku rechazó $key: ${result.output}")
                }
                val applied = readGlobalScale(key)
                if (kotlin.math.abs(applied - scale.toFloat()) > 0.01f) {
                    return@withContext OptimizerResult.Failure("Android no confirmó $key; valor leído: $applied")
                }
            }
            return@withContext OptimizerResult.Success("Animaciones configuradas a ${scale}x mediante Shizuku")
        }

        if (!hasWriteSecureSettings()) {
            return@withContext OptimizerResult.Failure("Vincula Shizuku para cambiar animaciones sin computadora")
        }

        val value = scale.toFloat()
        val keys = arrayOf(
            Settings.Global.WINDOW_ANIMATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            Settings.Global.ANIMATOR_DURATION_SCALE
        )
        val allWritten = keys.all { Settings.Global.putFloat(appContext.contentResolver, it, value) }
        if (allWritten) {
            val verified = keys.all {
                kotlin.math.abs(
                    Settings.Global.getFloat(appContext.contentResolver, it, -1f) - value
                ) <= 0.01f
            }
            if (verified) OptimizerResult.Success("Animaciones configuradas a ${scale}x y verificadas")
            else OptimizerResult.Failure("Android no confirmó el valor ${scale}x")
        }
        else OptimizerResult.Failure("Android no confirmó uno o más cambios")
    }

    suspend fun captureAnimationScales(): GameModeSnapshot? = withContext(Dispatchers.IO) {
        if (ShizukuAccess.status().permissionGranted) {
            GameModeSnapshot(
                window = readGlobalScale(Settings.Global.WINDOW_ANIMATION_SCALE),
                transition = readGlobalScale(Settings.Global.TRANSITION_ANIMATION_SCALE),
                animator = readGlobalScale(Settings.Global.ANIMATOR_DURATION_SCALE)
            )
        } else if (hasWriteSecureSettings()) {
            GameModeSnapshot(
                window = Settings.Global.getFloat(appContext.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f),
                transition = Settings.Global.getFloat(appContext.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f),
                animator = Settings.Global.getFloat(appContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            )
        } else null
    }

    suspend fun setGameMode(enabled: Boolean, snapshot: GameModeSnapshot?): OptimizerResult = withContext(Dispatchers.IO) {
        val values = if (enabled) listOf(0f, 0f, 0f) else listOf(
            snapshot?.window ?: 1f,
            snapshot?.transition ?: 1f,
            snapshot?.animator ?: 1f
        )
        val keys = listOf(
            Settings.Global.WINDOW_ANIMATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            Settings.Global.ANIMATOR_DURATION_SCALE
        )
        if (ShizukuAccess.status().permissionGranted) {
            keys.zip(values).forEach { (key, value) ->
                if (runShizukuCommand("settings", "put", "global", key, value.toString()).exitCode != 0) {
                    return@withContext OptimizerResult.Failure("No se pudo ${if (enabled) "activar" else "restaurar"} el modo juego")
                }
            }
            return@withContext OptimizerResult.Success(if (enabled) "Modo juego activo" else "Ajustes restaurados")
        }
        if (!hasWriteSecureSettings()) return@withContext OptimizerResult.Failure("Autoriza Shizuku para usar el modo juego")
        val written = keys.zip(values).all { (key, value) ->
            Settings.Global.putFloat(appContext.contentResolver, key, value)
        }
        if (written) OptimizerResult.Success(if (enabled) "Modo juego activo" else "Ajustes restaurados")
        else OptimizerResult.Failure("Android no confirmó el modo juego")
    }

    /** Solicita a Android que cierre procesos de fondo; requiere Shizuku autorizado. */
    suspend fun requestMemoryTrim(): OptimizerResult = withContext(Dispatchers.IO) {
        if (ShizukuAccess.status().permissionGranted) {
            return@withContext runShizukuCommand("am", "kill-all").toUserResult(
                success = "Procesos en segundo plano solicitados para cierre",
                failure = "Android rechazó el cierre de procesos"
            )
        }

        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return@withContext OptimizerResult.Failure("ActivityManager no disponible")
        runCatching {
            manager.killBackgroundProcesses(appContext.packageName)
            val memoryInfo = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(memoryInfo)
            OptimizerResult.Success("Limpieza limitada · RAM libre: ${memoryInfo.availMem / BYTES_PER_MB} MB")
        }.getOrElse { OptimizerResult.Failure("Vincula Shizuku para limpiar procesos de otras apps") }
    }

    private suspend fun runShizukuCommand(vararg command: String): CommandResult {
        val result = ShizukuCommandRunner.execute(arrayOf(*command))
        return CommandResult(result.exitCode, result.output)
    }

    private suspend fun readGlobalScale(key: String): Float =
        runShizukuCommand("settings", "get", "global", key).output.trim().toFloatOrNull() ?: 1f

    private fun CommandResult.toUserResult(success: String, failure: String): OptimizerResult =
        if (exitCode == 0) OptimizerResult.Success(success)
        else OptimizerResult.Failure(if (output.isBlank()) failure else "$failure: $output")

    private data class CommandResult(val exitCode: Int, val output: String)

    companion object {
        private const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
        private const val BYTES_PER_MB = 1_048_576L
        val DPI_RANGE = 360..650
    }
}
