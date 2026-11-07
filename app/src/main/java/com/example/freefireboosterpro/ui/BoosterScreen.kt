package com.example.freefireboosterpro.ui

import com.example.freefireboosterpro.shizuku.ShizukuStatus
import com.example.freefireboosterpro.overlay.OverlayConfig
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val Black = Color(0xFF080C11)
private val Surface = Color(0xFF121A24)
private val SurfaceRaised = Color(0xFF182331)
private val Crimson = Color(0xFFB8F34A)
private val Cyan = Color(0xFF65D8FF)
private val Amber = Color(0xFFFFC857)
private val TextPrimary = Color(0xFFF2F6FA)
private val TextSecondary = Color(0xFF9AAABC)

@Composable
fun BoosterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            background = Black,
            surface = Surface,
            primary = Crimson,
            secondary = Cyan,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        ),
        content = content
    )
}

@Composable
fun BoosterScreen(
    state: BoosterUiState,
    onDpiChanged: (Float) -> Unit,
    onRefreshPermission: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onRefreshApps: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onGraphicsProfileChanged: (GraphicsProfile) -> Unit,
    onOpenUsageAccess: () -> Unit,
    onApplyDpi: () -> Unit,
    onAnimationsChanged: (Boolean) -> Unit,
    onBoost: () -> Unit,
    onToggleOverlay: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onHudOpacityChanged: (Float) -> Unit,
    onHudScaleChanged: (Float) -> Unit,
    onHudMetricChanged: (String, Boolean) -> Unit,
    onRequestShizuku: () -> Unit,
    onRefreshShizuku: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Inicio", "Game Space", "HUD", "Sistema")
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Black).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("FREEFIRE", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("BOOSTER PRO", color = Crimson, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text("GAME SPACE  •  PERFORMANCE CORE", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Controla tu sesión sin modificar archivos del juego", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Surface,
                contentColor = Crimson,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title, fontSize = 12.sp) })
                }
            }
        }
        if (selectedTab == 0 || selectedTab == 1) {
            item { AppLauncherCard(state.installedApps, onRefreshApps, onLaunchApp) }
            item { GameModeCard(state.gameModeActive, state.usageAccessGranted, onOpenUsageAccess) }
            item { GraphicsProfileCard(state.graphicsProfile, onGraphicsProfileChanged) }
        }
        if (selectedTab == 1) {
            item { DpiSliderCard(state.dpi, !state.isBusy, onDpiChanged, onApplyDpi) }
            item { AnimationToggleCard((state.secureSettingsGranted || state.shizuku.permissionGranted) && !state.isBusy, state.instantAnimations, onAnimationsChanged) }
            item { BoostButton(enabled = !state.isBusy, onClick = onBoost) }
        }
        if (selectedTab == 2) {
            item { HudCard(state.overlayPermissionGranted, state.overlayRunning, !state.isBusy, onToggleOverlay, onOpenOverlayPermission) }
            item { HudSettingsCard(state.overlayConfig, !state.isBusy, onHudOpacityChanged, onHudScaleChanged, onHudMetricChanged) }
        }
        if (selectedTab == 3) {
            item { ShizukuCard(state.shizuku, !state.isBusy, onRequestShizuku, onRefreshShizuku) }
            item { PermissionCard(state.secureSettingsGranted, state.shizuku.permissionGranted, onRefreshPermission, onOpenDeveloperOptions) }
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

    state.launchingAppLabel?.let { label ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = Surface,
            title = { Text("PREPARANDO MODO JUEGO", color = Crimson, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(color = Cyan)
                    Spacer(Modifier.height(16.dp))
                    Text("Limpiando memoria…", color = Color.White)
                    Text("Optimizando animaciones para $label", color = Color.LightGray, fontSize = 12.sp)
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun AppLauncherCard(
    apps: List<InstalledAppInfo>,
    onRefresh: () -> Unit,
    onLaunch: (String) -> Unit
) {
    var showAll by remember { mutableStateOf(false) }
    BoosterCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("LANZADOR RÁPIDO", color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Abre una app y prepara tu sesión", color = TextSecondary, fontSize = 12.sp)
            }
            TextButton(onClick = onRefresh) { Text("Actualizar") }
        }
        if (apps.isEmpty()) {
            Text("No hay aplicaciones lanzables cargadas", color = Color.Gray)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps.take(6), key = { it.packageName }) { app ->
                    Button(
                        onClick = { onLaunch(app.packageName) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF292934)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            app.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            TextButton(onClick = { showAll = true }) { Text("Ver todas (${apps.size})") }
        }
    }

    if (showAll) {
        AlertDialog(
            onDismissRequest = { showAll = false },
            containerColor = Surface,
            title = { Text("Elegir aplicación", color = Color.White) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        TextButton(
                            onClick = {
                                showAll = false
                                onLaunch(app.packageName)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(app.label, color = Color.White, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAll = false }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun GraphicsProfileCard(
    selected: GraphicsProfile,
    onSelected: (GraphicsProfile) -> Unit
) {
    BoosterCard {
        Text("PERFIL VISUAL", color = Cyan, fontWeight = FontWeight.Bold)
        Text("Elige una recomendación para tu sesión", color = Color.LightGray, fontSize = 12.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(GraphicsProfile.values().toList()) { profile ->
                Button(
                    onClick = { onSelected(profile) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected == profile) Crimson else Color(0xFF292934)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(profile.title, fontSize = 12.sp) }
            }
        }
        Text(selected.subtitle, color = Color.White, fontSize = 13.sp)
        Text(
            "Este perfil no cambia el motor interno ni archivos de Free Fire. Confirma la calidad y FPS desde los ajustes del propio juego.",
            color = Color.Gray,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun GameModeCard(
    active: Boolean,
    usageAccessGranted: Boolean,
    onOpenUsageAccess: () -> Unit
) {
    BoosterCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("MODO JUEGO", color = Crimson, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(
                    if (active) "Activo · se restaurará al volver al booster" else "Se activa al abrir una app desde el lanzador",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Text(if (active) "ON" else "READY", color = if (active) Cyan else Color.Gray, fontWeight = FontWeight.Bold)
        }
        if (!usageAccessGranted) {
            Text(
                "Para restaurar automáticamente al cerrar la app, Android requiere Acceso de uso.",
                color = Color.Gray,
                fontSize = 12.sp
            )
            TextButton(onClick = onOpenUsageAccess) { Text("Conceder Acceso de uso") }
        }
    }
}

@Composable
private fun ShizukuCard(
    shizuku: ShizukuStatus,
    enabled: Boolean,
    onRequest: () -> Unit,
    onRefresh: () -> Unit
) {
    BoosterCard {
            Text("VINCULACIÓN SHIZUKU", color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(shizuku.mode, color = if (shizuku.permissionGranted) Cyan else Color.LightGray)
        Text(
            "Permiso opcional para APIs del sistema. No se solicita ni ejecuta nada sin tu aprobación.",
            color = Color.Gray,
            fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRequest, enabled = enabled && shizuku.available && !shizuku.permissionGranted) {
                Text(if (shizuku.available) "Vincular" else "No disponible")
            }
            TextButton(onClick = onRefresh, enabled = enabled) { Text("Comprobar") }
        }
    }
}

@Composable
private fun HudCard(
    overlayPermissionGranted: Boolean,
    running: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onOpenPermission: () -> Unit
) {
    BoosterCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
        Text("HUD DE RENDIMIENTO", color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(
                    when {
                        running -> "FPS · batería · RAM en tiempo real"
                        !overlayPermissionGranted -> "Necesita permiso de ventana flotante"
                        else -> "HUD apagado"
                    },
                    color = if (running) Cyan else Color.LightGray,
                    fontSize = 13.sp
                )
            }
            Switch(checked = running, onCheckedChange = { onToggle() }, enabled = enabled && overlayPermissionGranted)
        }
        if (!overlayPermissionGranted) {
            TextButton(onClick = onOpenPermission) {
                Text("Conceder permiso de overlay")
            }
        }
    }
}

@Composable
private fun HudSettingsCard(
    config: OverlayConfig,
    enabled: Boolean,
    onOpacityChanged: (Float) -> Unit,
    onScaleChanged: (Float) -> Unit,
    onMetricChanged: (String, Boolean) -> Unit
) {
    BoosterCard {
        Text("PERSONALIZAR HUD", color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text("Opacidad ${(config.opacity * 100).toInt()}%", color = Color.White)
        Slider(
            value = config.opacity,
            onValueChange = onOpacityChanged,
            valueRange = 0.35f..1f,
            enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = Crimson, activeTrackColor = Crimson)
        )
        Text("Tamaño ${(config.scale * 100).toInt()}%", color = Color.White)
        Slider(
            value = config.scale,
            onValueChange = onScaleChanged,
            valueRange = 0.75f..1.5f,
            enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan)
        )
        HudMetricToggle("FPS", config.showFps, enabled) { onMetricChanged("fps", it) }
        HudMetricToggle("Temperatura", config.showTemperature, enabled) { onMetricChanged("temperature", it) }
        HudMetricToggle("RAM libre", config.showMemory, enabled) { onMetricChanged("memory", it) }
    }
}

@Composable
private fun HudMetricToggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.LightGray)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun PermissionCard(
    secureGranted: Boolean,
    shizukuGranted: Boolean,
    onRefresh: () -> Unit,
    onOpenDeveloperOptions: () -> Unit
) {
    BoosterCard {
        Text("PERMISOS DEL SISTEMA", color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(
            when {
                shizukuGranted -> "Shizuku autorizado · sin computadora"
                secureGranted -> "WRITE_SECURE_SETTINGS concedido"
                else -> "Vincula Shizuku para habilitar las funciones avanzadas"
            },
            color = if (shizukuGranted || secureGranted) Cyan else Crimson
        )
        if (!secureGranted && !shizukuGranted) {
            Spacer(Modifier.height(6.dp))
            Text("La vinculación Shizuku mediante depuración inalámbrica sustituye el ADB tradicional.", color = Color.LightGray)
        }
        TextButton(onClick = onOpenDeveloperOptions) { Text("Abrir opciones de desarrollador") }
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
        Text("DENSIDAD / DPI", color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
                Text("ANIMACIONES DEL SISTEMA", color = TextPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}
