package com.example.freefireboosterpro.ui

import android.app.Application
import android.app.AppOpsManager
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.freefireboosterpro.data.OptimizerResult
import com.example.freefireboosterpro.data.GameModeSnapshot
import com.example.freefireboosterpro.game.GameModeMonitorService
import com.example.freefireboosterpro.data.SystemOptimizer
import com.example.freefireboosterpro.overlay.FpsOverlayService
import com.example.freefireboosterpro.overlay.OverlayConfig
import com.example.freefireboosterpro.overlay.OverlayPreferences
import com.example.freefireboosterpro.shizuku.ShizukuAccess
import com.example.freefireboosterpro.shizuku.ShizukuStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

data class BoosterUiState(
    val dpi: Float = 420f,
    val instantAnimations: Boolean = false,
    val secureSettingsGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val overlayRunning: Boolean = false,
    val overlayConfig: OverlayConfig = OverlayConfig(),
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val gameModeActive: Boolean = false,
    val launchingAppLabel: String? = null,
    val usageAccessGranted: Boolean = false,
    val shizuku: ShizukuStatus = ShizukuStatus(),
    val isBusy: Boolean = false,
    val status: String = "Listo para optimizar"
)

class BoosterViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val optimizer = SystemOptimizer(application)
    private val _uiState = MutableStateFlow(BoosterUiState())
    val uiState: StateFlow<BoosterUiState> = _uiState.asStateFlow()
    private var gameModeSnapshot: GameModeSnapshot? = null

    init {
        refreshInstalledApps()
    }

    fun setDpi(value: Float) {
        _uiState.value = _uiState.value.copy(dpi = value.coerceIn(360f, 650f))
    }

    fun refreshPermission() {
        _uiState.value = _uiState.value.copy(
            secureSettingsGranted = optimizer.hasWriteSecureSettings(),
            overlayPermissionGranted = Settings.canDrawOverlays(app),
            overlayRunning = FpsOverlayService.isRunning(),
            overlayConfig = OverlayPreferences.read(app),
            usageAccessGranted = hasUsageAccess(),
            shizuku = ShizukuAccess.status()
        )
    }

    fun refreshShizuku() {
        _uiState.value = _uiState.value.copy(shizuku = ShizukuAccess.status())
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                app.packageManager.queryIntentActivities(launcherIntent, 0)
                    .asSequence()
                    .map { resolveInfo ->
                        InstalledAppInfo(
                            label = resolveInfo.loadLabel(app.packageManager).toString(),
                            packageName = resolveInfo.activityInfo.packageName
                        )
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            }
            _uiState.value = _uiState.value.copy(installedApps = apps)
        }
    }

    fun launchApp(packageName: String) {
        val launchIntent = app.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            _uiState.value = _uiState.value.copy(status = "No se encontró un lanzador para esa app")
            return
        }
        val label = _uiState.value.installedApps.firstOrNull { it.packageName == packageName }?.label
        viewModelScope.launch {
            val snapshot = optimizer.captureAnimationScales()
            val cleanup = optimizer.requestMemoryTrim()
            val mode = optimizer.setGameMode(true, snapshot)
            gameModeSnapshot = snapshot
            _uiState.value = _uiState.value.copy(
                launchingAppLabel = label ?: "aplicación",
                gameModeActive = mode is OptimizerResult.Success,
                status = when (cleanup) {
                    is OptimizerResult.Success -> "Limpiando memoria y activando modo juego…"
                    is OptimizerResult.Failure -> "Activando modo juego…"
                }
            )
            delay(850L)
            if (mode is OptimizerResult.Success && snapshot != null && hasUsageAccess()) {
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, GameModeMonitorService::class.java).apply {
                        putExtra(GameModeMonitorService.EXTRA_TARGET, packageName)
                        putExtra(GameModeMonitorService.EXTRA_WINDOW, snapshot.window)
                        putExtra(GameModeMonitorService.EXTRA_TRANSITION, snapshot.transition)
                        putExtra(GameModeMonitorService.EXTRA_ANIMATOR, snapshot.animator)
                    }
                )
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(launchIntent)
            _uiState.value = _uiState.value.copy(
                launchingAppLabel = null,
                status = "Modo juego activo para ${label ?: "la aplicación"}"
            )
        }
    }

    fun restoreGameMode() {
        if (!_uiState.value.gameModeActive) return
        viewModelScope.launch {
            app.stopService(Intent(app, GameModeMonitorService::class.java))
            val result = optimizer.setGameMode(false, gameModeSnapshot)
            gameModeSnapshot = null
            _uiState.value = _uiState.value.copy(
                gameModeActive = false,
                status = when (result) {
                    is OptimizerResult.Success -> "Aplicación cerrada · ajustes restaurados"
                    is OptimizerResult.Failure -> result.message
                }
            )
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = app.getSystemService(AppOpsManager::class.java)
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            app.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun setHudOpacity(value: Float) = updateHud { copy(opacity = value) }

    fun setHudScale(value: Float) = updateHud { copy(scale = value) }

    fun setHudMetrics(
        showFps: Boolean? = null,
        showTemperature: Boolean? = null,
        showMemory: Boolean? = null
    ) = updateHud {
        copy(
            showFps = showFps ?: this.showFps,
            showTemperature = showTemperature ?: this.showTemperature,
            showMemory = showMemory ?: this.showMemory
        )
    }

    private fun updateHud(change: OverlayConfig.() -> OverlayConfig) {
        val config = change(_uiState.value.overlayConfig)
        OverlayPreferences.write(app, config)
        _uiState.value = _uiState.value.copy(
            overlayConfig = config,
            status = "Preferencias del HUD guardadas"
        )
    }

    fun requestShizukuPermission() {
        val status = ShizukuAccess.status()
        when {
            !status.available -> _uiState.value = _uiState.value.copy(
                status = "Inicia Shizuku desde su aplicación oficial y vuelve a comprobar"
            )
            status.permissionGranted -> _uiState.value = _uiState.value.copy(
                status = "Shizuku ya está autorizado"
            )
            else -> {
                ShizukuAccess.requestPermission()
                _uiState.value = _uiState.value.copy(status = "Aprueba el permiso en Shizuku")
            }
        }
    }

    fun applyDpi() {
        runOperation(operation = { optimizer.applyDensity(_uiState.value.dpi.toInt()) })
    }

    fun setAnimations(instant: Boolean) {
        if (!_uiState.value.secureSettingsGranted && !_uiState.value.shizuku.permissionGranted) {
            _uiState.value = _uiState.value.copy(
                status = "Vincula Shizuku para cambiar animaciones sin computadora"
            )
            return
        }

        runOperation(
            operation = { optimizer.setAnimationScales(instant) },
            onSuccess = { result ->
                if (result is OptimizerResult.Success) {
                    _uiState.value = _uiState.value.copy(instantAnimations = instant)
                }
            }
        )
    }

    fun requestMemoryTrim() {
        runOperation(operation = { optimizer.requestMemoryTrim() })
    }

    fun toggleOverlay() {
        if (!Settings.canDrawOverlays(app)) {
            _uiState.value = _uiState.value.copy(
                overlayPermissionGranted = false,
                status = "Concede el permiso de ventana flotante para activar el HUD"
            )
            return
        }

        if (FpsOverlayService.isRunning()) {
            app.stopService(Intent(app, FpsOverlayService::class.java))
            _uiState.value = _uiState.value.copy(overlayRunning = false, status = "HUD detenido")
        } else {
            ContextCompat.startForegroundService(
                app,
                Intent(app, FpsOverlayService::class.java)
            )
            _uiState.value = _uiState.value.copy(overlayRunning = true, status = "HUD activo sobre tus juegos")
        }
    }

    private fun runOperation(
        operation: suspend () -> OptimizerResult,
        onSuccess: (OptimizerResult) -> Unit = {}
    ) {
        if (_uiState.value.isBusy) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, status = "Aplicando ajuste…")
            try {
                val result = operation()
                onSuccess(result)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    status = when (result) {
                        is OptimizerResult.Success -> result.message
                        is OptimizerResult.Failure -> result.message
                    }
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    status = "No se pudo aplicar el ajuste: ${error.message ?: "error inesperado"}"
                )
            }
        }
    }
}
