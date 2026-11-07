package com.example.freefireboosterpro.ui

import com.example.freefireboosterpro.shizuku.ShizukuStatus
import com.example.freefireboosterpro.overlay.OverlayConfig
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val Ink = Color(0xFF160C0C)
private val Panel = Color(0xFF241313)
private val PanelRaised = Color(0xFF321B18)
private val PanelSoft = Color(0xFF40231F)
private val Coral = Color(0xFFFFB0A7)
private val CoralStrong = Color(0xFFE86C5E)
private val Cream = Color(0xFFFFF3F0)
private val Muted = Color(0xFFC5AAA5)
private val Line = Color(0xFF73534D)
private val Success = Color(0xFF8DDA91)

@Composable
fun BoosterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            background = Ink,
            surface = Panel,
            surfaceVariant = PanelRaised,
            primary = Coral,
            secondary = CoralStrong,
            onBackground = Cream,
            onSurface = Cream,
            onSurfaceVariant = Muted
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
    onRefreshShizuku: () -> Unit,
    onOpenUpdates: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Medidor", "Game Space", "Sistema")

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Ink).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TopBar(onOpenUpdates) }
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Ink,
                contentColor = Coral,
                divider = { HorizontalDivider(color = Line.copy(alpha = .55f)) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }
        when (selectedTab) {
            0 -> {
                item { HudCard(state.overlayPermissionGranted, state.overlayRunning, !state.isBusy, onToggleOverlay, onOpenOverlayPermission) }
                item { HudSettingsCard(state.overlayConfig, !state.isBusy, onHudOpacityChanged, onHudScaleChanged, onHudMetricChanged) }
                item { UpdateCard(onOpenUpdates) }
            }
            1 -> {
                item { AppLauncherCard(state.installedApps, onRefreshApps, onLaunchApp) }
                item { GameModeCard(state.gameModeActive, state.usageAccessGranted, onOpenUsageAccess) }
                item { GraphicsProfileCard(state.graphicsProfile, onGraphicsProfileChanged) }
                item { DpiSliderCard(state.dpi, !state.isBusy, onDpiChanged, onApplyDpi) }
                item { AnimationToggleCard((state.secureSettingsGranted || state.shizuku.permissionGranted) && !state.isBusy, state.instantAnimations, onAnimationsChanged) }
                item { BoostButton(!state.isBusy, onBoost) }
            }
            else -> {
                item { ShizukuCard(state.shizuku, !state.isBusy, onRequestShizuku, onRefreshShizuku) }
                item { PermissionCard(state.secureSettingsGranted, state.shizuku.permissionGranted, onRefreshPermission, onOpenDeveloperOptions) }
                item { UpdateCard(onOpenUpdates) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                HorizontalDivider(color = Line.copy(alpha = .55f))
                Text(state.status, color = Coral, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Android puede limitar cambios protegidos según el fabricante.", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    state.launchingAppLabel?.let { label ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = Panel,
            title = { Text("PREPARANDO SESIÓN", color = Coral, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(color = Coral, strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Limpiando memoria y preparando recursos…", color = Cream)
                    Text(label, color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun TopBar(onOpenUpdates: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(CoralStrong, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text("⌁", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("FPS BOOSTER", color = Cream, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text("LIVE PERFORMANCE TOOL", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
            }
        }
        TextButton(onClick = onOpenUpdates) { Text("Actualizar", color = Coral, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun HudCard(permission: Boolean, running: Boolean, enabled: Boolean, onToggle: () -> Unit, onPermission: () -> Unit) {
    BoosterCard {
        SectionHeader("FPS OVERLAY", if (running) "ACTIVO" else "LISTO", if (running) Success else Muted)
        Text(
            when {
                running -> "Medición en tiempo real sobre tus juegos"
                !permission -> "Concede permiso para dibujar sobre otras apps"
                else -> "Panel compacto de FPS, temperatura y memoria"
            }, color = Muted, fontSize = 13.sp
        )
        Button(
            onClick = if (permission) onToggle else onPermission,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (running) PanelSoft else Coral, contentColor = if (running) Cream else Ink)
        ) { Text(if (!permission) "CONCEDER PERMISO" else if (running) "DETENER OVERLAY" else "INICIAR OVERLAY", fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun HudSettingsCard(config: OverlayConfig, enabled: Boolean, onOpacity: (Float) -> Unit, onScale: (Float) -> Unit, onMetric: (String, Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    var provider by remember { mutableStateOf("Choreographer") }
    var style by remember { mutableStateOf("Compact") }
    var position by remember { mutableStateOf("Arriba izquierda") }
    BoosterCard {
        ExpandableHeader("OVERLAY SETTINGS", "Ajustes del panel", expanded) { expanded = !expanded }
        if (expanded) {
            Text("FPS MEASUREMENT PROVIDER", color = Coral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton("Choreographer", "Display pace", provider == "Choreographer") { provider = "Choreographer" }
                ChoiceButton("SurfaceFlinger", "Game FPS", provider == "SurfaceFlinger") { provider = "SurfaceFlinger" }
            }
            HorizontalDivider(color = Line.copy(alpha = .5f))
            LabeledSlider("Tamaño del texto", "${(config.scale * 13).toInt()}sp", config.scale, .75f..1.5f, enabled, onScale)
            LabeledSlider("Opacidad del texto", "${(config.opacity * 100).toInt()}%", config.opacity, .35f..1f, enabled, onOpacity)
            Text("MÉTRICAS VISIBLES", color = Coral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            MetricSwitch("FPS", config.showFps, enabled) { onMetric("fps", it) }
            MetricSwitch("Temperatura de batería", config.showTemperature, enabled) { onMetric("temperature", it) }
            MetricSwitch("RAM disponible", config.showMemory, enabled) { onMetric("memory", it) }
            MetricSwitch("Milisegundos por frame", config.showFrameTime, enabled) { onMetric("frame_time", it) }
            MetricSwitch("Nivel de batería", config.showBatteryLevel, enabled) { onMetric("battery", it) }
            Text("ESTILO DEL OVERLAY", color = Coral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton("Compact", "Pill limpio", style == "Compact") { style = "Compact" }
                ChoiceButton("RTSS", "Modo técnico", style == "RTSS") { style = "RTSS" }
            }
            Text("POSICIÓN PREDEFINIDA", color = Coral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Arriba izquierda", "Arriba derecha", "Abajo derecha").forEach { value ->
                    ChoiceButton(value, "", position == value) { position = value }
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Cream, fontSize = 13.sp)
            Text(valueLabel, color = Coral, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, enabled = enabled, colors = SliderDefaults.colors(thumbColor = Coral, activeTrackColor = Coral))
    }
}

@Composable
private fun ChoiceButton(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier,
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) PanelSoft else Panel, contentColor = if (selected) Coral else Cream),
        border = BorderStroke(1.dp, if (selected) Coral else Line),
        shape = RoundedCornerShape(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MetricSwitch(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Cream, fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun AppLauncherCard(apps: List<InstalledAppInfo>, onRefresh: () -> Unit, onLaunch: (String) -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    BoosterCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("GAME LAUNCHER", color = Cream, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Lanza y prepara tu sesión automáticamente", color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = onRefresh) { Text("Actualizar", color = Coral) }
        }
        if (apps.isEmpty()) Text("No hay aplicaciones lanzables cargadas", color = Muted)
        else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps.take(5), key = { it.packageName }) { app ->
                    Button(onClick = { onLaunch(app.packageName) }, colors = ButtonDefaults.buttonColors(containerColor = PanelSoft), shape = RoundedCornerShape(14.dp)) {
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    }
                }
            }
            TextButton(onClick = { showAll = true }) { Text("Ver todas (${apps.size})", color = Coral) }
        }
    }
    if (showAll) AlertDialog(
        onDismissRequest = { showAll = false }, containerColor = Panel,
        title = { Text("Elegir aplicación", color = Cream) },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) { items(apps, key = { it.packageName }) { app -> TextButton(onClick = { showAll = false; onLaunch(app.packageName) }, modifier = Modifier.fillMaxWidth()) { Text(app.label, color = Cream, modifier = Modifier.fillMaxWidth()) } } } },
        confirmButton = { TextButton(onClick = { showAll = false }) { Text("Cerrar", color = Coral) } }
    )
}

@Composable
private fun GameModeCard(active: Boolean, usageAccessGranted: Boolean, onOpenUsageAccess: () -> Unit) {
    BoosterCard {
        SectionHeader("GAME MODE", if (active) "ACTIVO" else "READY", if (active) Success else Coral)
        Text(if (active) "Recursos priorizados para la aplicación lanzada" else "Limpieza y restauración automática al cerrar", color = Muted, fontSize = 13.sp)
        if (!usageAccessGranted) TextButton(onClick = onOpenUsageAccess) { Text("Conceder acceso de uso", color = Coral) }
    }
}

@Composable
private fun GraphicsProfileCard(selected: GraphicsProfile, onSelected: (GraphicsProfile) -> Unit) {
    BoosterCard {
        Text("PERFIL DE RENDIMIENTO", color = Coral, fontWeight = FontWeight.Bold)
        Text("Un preset para orientar tu sesión; confirma los gráficos dentro del juego.", color = Muted, fontSize = 12.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(GraphicsProfile.values().toList()) { profile -> ChoiceButton(profile.title, "", selected == profile) { onSelected(profile) } } }
        Text(selected.subtitle, color = Cream, fontSize = 13.sp)
    }
}

@Composable
private fun DpiSliderCard(dpi: Float, enabled: Boolean, onDpiChanged: (Float) -> Unit, onApply: () -> Unit) {
    BoosterCard {
        ExpandableHeader("DENSIDAD DEL SISTEMA", "${dpi.toInt()} dpi", true) {}
        Slider(value = dpi, onValueChange = onDpiChanged, valueRange = 360f..650f, enabled = enabled, colors = SliderDefaults.colors(thumbColor = Coral, activeTrackColor = Coral))
        Button(onClick = onApply, enabled = enabled, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Ink), shape = RoundedCornerShape(14.dp)) { Text("APLICAR DENSIDAD", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun AnimationToggleCard(enabled: Boolean, instant: Boolean, onToggle: (Boolean) -> Unit) {
    BoosterCard {
        ExpandableHeader("ANIMACIONES DEL SISTEMA", if (instant) "0.0x" else "0.5x", true) {}
        Text("Reduce transiciones globales para mejorar la respuesta percibida.", color = Muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(if (instant) "Instantáneo" else "Rápido", color = Cream); Switch(checked = instant, onCheckedChange = onToggle, enabled = enabled) }
    }
}

@Composable
private fun BoostButton(enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Ink), shape = RoundedCornerShape(16.dp)) { Text("BOOST DE MEMORIA", fontWeight = FontWeight.Black) }
}

@Composable
private fun ShizukuCard(shizuku: ShizukuStatus, enabled: Boolean, onRequest: () -> Unit, onRefresh: () -> Unit) {
    BoosterCard {
        SectionHeader("SHIZUKU", if (shizuku.permissionGranted) "CONECTADO" else "OPCIONAL", if (shizuku.permissionGranted) Success else Coral)
        Text(shizuku.mode, color = Muted)
        Text("Permite ajustes del sistema sin root ni computadora, siempre con tu aprobación.", color = Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onRequest, enabled = enabled && shizuku.available && !shizuku.permissionGranted, colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Ink)) { Text(if (shizuku.available) "VINCULAR" else "NO DISPONIBLE") }; TextButton(onClick = onRefresh, enabled = enabled) { Text("Comprobar", color = Coral) } }
    }
}

@Composable
private fun PermissionCard(secureGranted: Boolean, shizukuGranted: Boolean, onRefresh: () -> Unit, onOpenDeveloperOptions: () -> Unit) {
    BoosterCard {
        Text("PERMISOS DEL SISTEMA", color = Coral, fontWeight = FontWeight.Bold)
        Text(if (shizukuGranted || secureGranted) "Permisos avanzados disponibles" else "Vincula Shizuku para habilitar funciones avanzadas", color = if (shizukuGranted || secureGranted) Success else Coral)
        TextButton(onClick = onOpenDeveloperOptions) { Text("Abrir opciones de desarrollador", color = Coral) }
        TextButton(onClick = onRefresh) { Text("Comprobar de nuevo", color = Coral) }
    }
}

@Composable
private fun UpdateCard(onOpenUpdates: () -> Unit) {
    BoosterCard {
        SectionHeader("ACTUALIZACIONES", "VERSIÓN ONLINE", Coral)
        Text("Consulta la última APK, novedades y correcciones desde el repositorio oficial.", color = Muted, fontSize = 12.sp)
        Button(onClick = onOpenUpdates, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PanelSoft), border = BorderStroke(1.dp, Coral), shape = RoundedCornerShape(14.dp)) { Text("BUSCAR ACTUALIZACIÓN", color = Coral, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SectionHeader(title: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(title, color = Cream, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ExpandableHeader(title: String, subtitle: String, expanded: Boolean, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().animateContentSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) { Text(title, color = Cream, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Text(subtitle, color = Muted, fontSize = 12.sp) }
        TextButton(onClick = onClick) { Text(if (expanded) "⌃" else "⌄", color = Coral, fontSize = 20.sp) }
    }
}

@Composable
private fun BoosterCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Line.copy(alpha = .65f)), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}
