package com.example.freefireboosterpro.ui

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.freefireboosterpro.data.OptimizerResult
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

data class BoosterUiState(
    val dpi: Float = 420f,
    val instantAnimations: Boolean = false,
    val secureSettingsGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val overlayRunning: Boolean = false,
    val overlayConfig: OverlayConfig = OverlayConfig(),
    val shizuku: ShizukuStatus = ShizukuStatus(),
    val isBusy: Boolean = false,
    val status: String = "Listo para optimizar"
)

class BoosterViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val optimizer = SystemOptimizer(application)
    private val _uiState = MutableStateFlow(BoosterUiState())
    val uiState: StateFlow<BoosterUiState> = _uiState.asStateFlow()

    fun setDpi(value: Float) {
        _uiState.value = _uiState.value.copy(dpi = value.coerceIn(360f, 650f))
    }

    fun refreshPermission() {
        _uiState.value = _uiState.value.copy(
            secureSettingsGranted = optimizer.hasWriteSecureSettings(),
            overlayPermissionGranted = Settings.canDrawOverlays(app),
            overlayRunning = FpsOverlayService.isRunning(),
            overlayConfig = OverlayPreferences.read(app),
            shizuku = ShizukuAccess.status()
        )
    }

    fun refreshShizuku() {
        _uiState.value = _uiState.value.copy(shizuku = ShizukuAccess.status())
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
            val result = operation()
            onSuccess(result)
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                status = when (result) {
                    is OptimizerResult.Success -> result.message
                    is OptimizerResult.Failure -> result.message
                }
            )
        }
    }
}
