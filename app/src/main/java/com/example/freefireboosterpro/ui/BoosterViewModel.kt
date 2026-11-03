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
            overlayRunning = FpsOverlayService.isRunning()
        )
    }

    fun applyDpi() {
        runOperation(operation = { optimizer.applyDensity(_uiState.value.dpi.toInt()) })
    }

    fun setAnimations(instant: Boolean) {
        if (!_uiState.value.secureSettingsGranted) {
            _uiState.value = _uiState.value.copy(
                status = "Concede WRITE_SECURE_SETTINGS antes de cambiar animaciones"
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
