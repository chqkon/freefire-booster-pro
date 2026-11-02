package com.example.freefireboosterpro

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private val Black = Color(0xFF0A0A0A)
private val Surface = Color(0xFF1E1E1E)
private val Crimson = Color(0xFFFF2A5F)
private val Cyan = Color(0xFF00D9FF)

class MainActivity : ComponentActivity() {
    private val appPackage = "com.example.freefireboosterpro"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BoosterTheme {
                BoosterScreen(
                    hasSecureSettings = hasWriteSecureSettings(),
                    onApplyDpi = { dpi -> runDpiCommand(dpi) },
                    onApplyAnimations = { instant -> setAnimationScales(instant) },
                    onBoost = { requestAllowedProcessTrim() },
                    onRefreshPermission = { hasWriteSecureSettings() }
                )
            }
        }
    }

    /** WRITE_SECURE_SETTINGS no es un permiso runtime normal. */
    private fun hasWriteSecureSettings(): Boolean =
        checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Intenta ejecutar wm sin pasar por un shell concatenado: evita inyección de comandos.
     * En un dispositivo no rooteado Android normalmente lo rechazará para una app normal.
     */
    private suspend fun runDpiCommand(dpi: Int): Result<String> = withContext(Dispatchers.IO) {
        if (dpi !in 360..650) return@withContext Result.failure(IllegalArgumentException("DPI fuera de rango"))
        try {
            val process = Runtime.getRuntime().exec(arrayOf("wm", "density", dpi.toString()))
            val exitCode = process.waitFor()
            if (exitCode == 0) Result.success("Densidad aplicada: $dpi dpi")
            else Result.failure(IOException("wm density fue rechazado (código $exitCode)"))
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private suspend fun setAnimationScales(instant: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (!hasWriteSecureSettings()) {
            return@withContext Result.failure(SecurityException("Falta WRITE_SECURE_SETTINGS"))
        }
        val scale = if (instant) 0.0f else 0.5f
        val resolver = contentResolver
        val keys = listOf(
            Settings.Global.WINDOW_ANIMATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            Settings.Global.ANIMATOR_DURATION_SCALE
        )
        try {
            keys.forEach { Settings.Global.putFloat(resolver, it, scale) }
            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    /**
     * Android no permite a una app de terceros limpiar libremente la RAM de otras apps.
     * Esta llamada es deliberadamente limitada a nuestro propio paquete y puede ser ignorada
     * por el sistema. No mata Free Fire ni garantiza FPS.
     */
    private suspend fun requestAllowedProcessTrim(): Result<String> = withContext(Dispatchers.IO) {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return@withContext try {
            manager.killBackgroundProcesses(packageName)
            System.gc()
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            Result.success("Limpieza ligera solicitada; RAM disponible: ${info.availMem / 1_048_576} MB")
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }
}

@Composable
private fun BoosterTheme(content: @Composable () -> Unit) {
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
private fun BoosterScreen(
    hasSecureSettings: Boolean,
    onApplyDpi: suspend (Int) -> Result<String>,
    onApplyAnimations: suspend (Boolean) -> Result<Unit>,
    onBoost: suspend () -> Result<String>,
    onRefreshPermission: () -> Boolean
) {
    var dpi by remember { mutableFloatStateOf(420f) }
    var instantAnimations by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(hasSecureSettings) }
    var status by remember { mutableStateOf("Listo para optimizar") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { permissionGranted = onRefreshPermission() }

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
                granted = permissionGranted,
                onRefresh = { permissionGranted = onRefreshPermission() }
            )
        }
        item {
            DpiSliderCard(
                dpi = dpi,
                enabled = !busy,
                onDpiChanged = { dpi = it },
                onApply = {
                    scope.launch {
                        busy = true
                        status = onApplyDpi(dpi.toInt()).fold({ it }, { "DPI no aplicado: ${it.message}" })
                        busy = false
                    }
                }
            )
        }
        item {
            AnimationToggleCard(
                enabled = permissionGranted && !busy,
                instant = instantAnimations,
                onToggle = { value ->
                    instantAnimations = value
                    scope.launch {
                        busy = true
                        status = onApplyAnimations(value).fold({ "Animaciones: ${if (value) "0.0x" else "0.5x"}" }, { "Animaciones no aplicadas: ${it.message}" })
                        busy = false
                    }
                }
            )
        }
        item {
            BoostButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    status = onBoost().fold({ it }, { "Boost limitado: ${it.message}" })
                    busy = false
                }
            })
        }
        item {
            Divider(color = Surface)
            Text(status, color = Cyan, fontSize = 13.sp)
            Text("Nota: Android puede ignorar cambios protegidos y los resultados dependen del fabricante.", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PermissionCard(granted: Boolean, onRefresh: () -> Unit) {
    BoosterCard {
        Text("PERMISO ADB", color = Cyan, fontWeight = FontWeight.Bold)
        Text(if (granted) "WRITE_SECURE_SETTINGS concedido" else "Se necesita autorización ADB", color = if (granted) Cyan else Crimson)
        if (!granted) {
            Spacer(Modifier.height(6.dp))
            Text("En un PC ejecuta:", color = Color.LightGray)
            Text("adb shell pm grant com.example.freefireboosterpro android.permission.WRITE_SECURE_SETTINGS", color = Color.White, fontSize = 12.sp)
        }
        TextButton(onClick = onRefresh) { Text("Comprobar de nuevo") }
    }
}

@Composable
private fun DpiSliderCard(dpi: Float, enabled: Boolean, onDpiChanged: (Float) -> Unit, onApply: () -> Unit) {
    BoosterCard {
        Text("DENSIDAD / DPI", color = Cyan, fontWeight = FontWeight.Bold)
        Text("${dpi.toInt()} dpi", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Slider(value = dpi, onValueChange = onDpiChanged, valueRange = 360f..650f, enabled = enabled, colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Crimson, activeTrackColor = Crimson))
        Button(onClick = onApply, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Aplicar densidad") }
    }
}

@Composable
private fun AnimationToggleCard(enabled: Boolean, instant: Boolean, onToggle: (Boolean) -> Unit) {
    BoosterCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
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
    Card(colors = CardDefaults.cardColors(containerColor = Crimson), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Crimson, contentColor = Color.White)) {
            Text("BOOST DE MEMORIA", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BoosterCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}
