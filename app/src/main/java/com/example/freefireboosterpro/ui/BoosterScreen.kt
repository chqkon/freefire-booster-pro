package com.example.freefireboosterpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Black = Color(0xFF0A0A0A)
private val Surface = Color(0xFF1E1E1E)
private val Crimson = Color(0xFFFF2A5F)
private val Cyan = Color(0xFF00D9FF)

@Composable
fun BoosterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            background = Black,
            surface = Surface,
            primary = Crimson,
            secondary = Cyan
        ),
        content = content
    )
}

@Composable
fun BoosterScreen(
    state: BoosterUiState,
    onDpiChanged: (Float) -> Unit,
    onRefreshPermission: () -> Unit,
    onApplyDpi: () -> Unit,
    onAnimationsChanged: (Boolean) -> Unit,
    onBoost: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Black).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("FREEFIRE BOOSTER PRO", color = Crimson, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("Ajustes del sistema, sin modificar archivos del juego", color = Color.LightGray)
        }
        item {
            PermissionCard(
                granted = state.secureSettingsGranted,
                onRefresh = onRefreshPermission
            )
        }
        item {
            DpiSliderCard(
                dpi = state.dpi,
                enabled = !state.isBusy,
                onDpiChanged = onDpiChanged,
                onApply = onApplyDpi
            )
        }
        item {
            AnimationToggleCard(
                enabled = state.secureSettingsGranted && !state.isBusy,
                instant = state.instantAnimations,
                onToggle = onAnimationsChanged
            )
        }
        item {
            BoostButton(enabled = !state.isBusy, onClick = onBoost)
        }
        item {
            HorizontalDivider(color = Surface)
            Text(state.status, color = Cyan, fontSize = 13.sp)
            Text(
                "Android puede ignorar cambios protegidos; los resultados dependen del fabricante.",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PermissionCard(granted: Boolean, onRefresh: () -> Unit) {
    BoosterCard {
        Text("PERMISO ADB", color = Cyan, fontWeight = FontWeight.Bold)
        Text(
            if (granted) "WRITE_SECURE_SETTINGS concedido" else "Se necesita autorización ADB",
            color = if (granted) Cyan else Crimson
        )
        if (!granted) {
            Spacer(Modifier.height(6.dp))
            Text("Ejecuta en un PC o shell ADB autorizado:", color = Color.LightGray)
            Text(
                "adb shell pm grant com.example.freefireboosterpro android.permission.WRITE_SECURE_SETTINGS",
                color = Color.White,
                fontSize = 12.sp
            )
        }
        TextButton(onClick = onRefresh) { Text("Comprobar de nuevo") }
    }
}

@Composable
private fun DpiSliderCard(
    dpi: Float,
    enabled: Boolean,
    onDpiChanged: (Float) -> Unit,
    onApply: () -> Unit
) {
    BoosterCard {
        Text("DENSIDAD / DPI", color = Cyan, fontWeight = FontWeight.Bold)
        Text("${dpi.toInt()} dpi", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Slider(
            value = dpi,
            onValueChange = onDpiChanged,
            valueRange = 360f..650f,
            enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = Crimson, activeTrackColor = Crimson)
        )
        Button(onClick = onApply, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("Aplicar densidad")
        }
    }
}

@Composable
private fun AnimationToggleCard(enabled: Boolean, instant: Boolean, onToggle: (Boolean) -> Unit) {
    BoosterCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("ANIMACIONES DEL SISTEMA", color = Cyan, fontWeight = FontWeight.Bold)
                Text(if (instant) "Modo instantáneo · 0.0x" else "Modo rápido · 0.5x", color = Color.White)
            }
            Switch(checked = instant, onCheckedChange = onToggle, enabled = enabled)
        }
    }
}

@Composable
private fun BoostButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Crimson, contentColor = Color.White)
    ) {
        Text("BOOST DE MEMORIA", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BoosterCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}
