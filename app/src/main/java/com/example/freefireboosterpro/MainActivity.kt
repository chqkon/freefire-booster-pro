package com.example.freefireboosterpro

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.freefireboosterpro.ui.BoosterScreen
import com.example.freefireboosterpro.ui.BoosterTheme
import com.example.freefireboosterpro.ui.BoosterViewModel
import com.example.freefireboosterpro.shizuku.ShizukuAccess
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val viewModel: BoosterViewModel by viewModels()
    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == ShizukuAccess.REQUEST_CODE) viewModel.refreshShizuku()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(shizukuListener)
        setContent {
            val state = viewModel.uiState.collectAsStateWithLifecycle()
            BoosterTheme {
                BoosterScreen(
                    state = state.value,
                    onDpiChanged = viewModel::setDpi,
                    onRefreshPermission = viewModel::refreshPermission,
                    onApplyDpi = viewModel::applyDpi,
                    onAnimationsChanged = viewModel::setAnimations,
                    onBoost = viewModel::requestMemoryTrim,
                    onToggleOverlay = viewModel::toggleOverlay,
                    onOpenOverlayPermission = ::openOverlayPermission,
                    onRequestShizuku = viewModel::requestShizukuPermission,
                    onRefreshShizuku = viewModel::refreshShizuku
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // El usuario puede volver de Ajustes/ADB con el permiso recién concedido.
        viewModel.refreshPermission()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
        super.onDestroy()
    }

    private fun openOverlayPermission() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }
}
