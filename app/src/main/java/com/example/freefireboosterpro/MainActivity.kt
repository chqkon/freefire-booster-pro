package com.example.freefireboosterpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.freefireboosterpro.ui.BoosterScreen
import com.example.freefireboosterpro.ui.BoosterTheme
import com.example.freefireboosterpro.ui.BoosterViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: BoosterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state = viewModel.uiState.collectAsStateWithLifecycle()
            BoosterTheme {
                BoosterScreen(
                    state = state.value,
                    onDpiChanged = viewModel::setDpi,
                    onRefreshPermission = viewModel::refreshPermission,
                    onApplyDpi = viewModel::applyDpi,
                    onAnimationsChanged = viewModel::setAnimations,
                    onBoost = viewModel::requestMemoryTrim
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // El usuario puede volver de Ajustes/ADB con el permiso recién concedido.
        viewModel.refreshPermission()
    }
}
