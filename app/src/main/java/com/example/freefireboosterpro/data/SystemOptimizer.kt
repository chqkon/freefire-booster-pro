package com.example.freefireboosterpro.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface OptimizerResult {
    data class Success(val message: String) : OptimizerResult
    data class Failure(val message: String) : OptimizerResult
}

/** Acceso aislado a APIs del sistema. No guarda referencias a Activity ni a Views. */
class SystemOptimizer(context: Context) {
    private val appContext = context.applicationContext

    fun hasWriteSecureSettings(): Boolean =
        appContext.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    suspend fun applyDensity(dpi: Int): OptimizerResult = withContext(Dispatchers.IO) {
        if (dpi !in DPI_RANGE) {
            return@withContext OptimizerResult.Failure("El DPI debe estar entre 360 y 650")
        }

        try {
            // Argumentos separados: no se construye una línea de shell concatenada.
            val process = Runtime.getRuntime().exec(arrayOf("wm", "density", dpi.toString()))
            val finished = process.waitFor(COMMAND_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return@withContext OptimizerResult.Failure("El comando DPI agotó el tiempo de espera")
            }
            if (process.exitValue() == 0) {
                OptimizerResult.Success("Densidad aplicada: $dpi dpi")
            } else {
                OptimizerResult.Failure("Android rechazó wm density; requiere shell autorizado o root")
            }
        } catch (error: SecurityException) {
            OptimizerResult.Failure("Permiso insuficiente para cambiar la densidad")
        } catch (error: IOException) {
            OptimizerResult.Failure("No se pudo ejecutar wm density")
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            OptimizerResult.Failure("La operación DPI fue cancelada")
        }
    }

    suspend fun setAnimationScales(instant: Boolean): OptimizerResult = withContext(Dispatchers.IO) {
        if (!hasWriteSecureSettings()) {
            return@withContext OptimizerResult.Failure("Concede WRITE_SECURE_SETTINGS mediante ADB y vuelve a comprobar")
        }

        val scale = if (instant) 0.0f else 0.5f
        val keys = arrayOf(
            Settings.Global.WINDOW_ANIMATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            Settings.Global.ANIMATOR_DURATION_SCALE
        )

        try {
            val allWritten = keys.all { key ->
                Settings.Global.putFloat(appContext.contentResolver, key, scale)
            }
            if (allWritten) OptimizerResult.Success("Animaciones configuradas a ${scale}x")
            else OptimizerResult.Failure("Android no confirmó uno o más cambios")
        } catch (error: SecurityException) {
            OptimizerResult.Failure("WRITE_SECURE_SETTINGS ya no está concedido")
        }
    }

    suspend fun requestMemoryTrim(): OptimizerResult = withContext(Dispatchers.IO) {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return@withContext OptimizerResult.Failure("ActivityManager no disponible")

        try {
            // Android limita qué procesos puede finalizar una app de terceros.
            manager.killBackgroundProcesses(appContext.packageName)
            val memoryInfo = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(memoryInfo)
            val availableMb = memoryInfo.availMem / BYTES_PER_MB
            OptimizerResult.Success("Solicitud de limpieza enviada · RAM libre: ${availableMb} MB")
        } catch (error: SecurityException) {
            OptimizerResult.Failure("El sistema rechazó la limpieza de procesos")
        }
    }

    companion object {
        private const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
        private const val COMMAND_TIMEOUT_MS = 4_000L
        private const val BYTES_PER_MB = 1_048_576L
        val DPI_RANGE = 360..650
    }
}
