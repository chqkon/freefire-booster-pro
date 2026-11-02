# Módulos avanzados para FreeFire Booster Pro

> Estos módulos usan APIs públicas de Android. Root, overlay y VPN requieren autorización explícita del usuario. Ningún módulo modifica archivos de Free Fire ni puede garantizar FPS, ping o ausencia de baneos.

## 1. AndroidManifest.xml

Añade estos permisos y servicios al `AndroidManifest.xml` existente:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application ...>
        <service
            android:name=".overlay.PerformanceOverlayService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="performance_overlay" />
        </service>

        <service
            android:name=".network.DnsVpnService"
            android:permission="android.permission.BIND_VPN_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.net.VpnService" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

En Android 13 o superior solicita `POST_NOTIFICATIONS` en runtime antes de iniciar el foreground service. En versiones que no acepten `specialUse`, usa el tipo de foreground service permitido por el target SDK de tu proyecto y justifícalo en Play Console.

`BIND_VPN_SERVICE` no se solicita como permiso runtime: se declara como `android:permission` en el servicio.

---

## 2. Root opcional y seguro

No existe una API pública para saber si el dispositivo está rooteado. La detección es heurística. No ejecutes comandos root automáticamente sin un switch de consentimiento persistente: root puede cambiar permanentemente la configuración del dispositivo.

### `root/RootAccess.kt`

```kotlin
package com.example.freefireboosterpro.root

import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object RootAccess {
    fun hasSuBinary(): Boolean {
        val candidates = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su"
        )
        return candidates.any { java.io.File(it).exists() }
    }

    /** Comprueba si el gestor de root permite ejecutar un comando inofensivo. */
    fun canExecuteRoot(): Boolean = runRoot("id")
        .getOrNull()
        ?.contains("uid=0") == true

    /** Ejecuta un comando como root sin concatenar una línea de shell controlada por UI. */
    fun runRoot(vararg args: String): Result<String> {
        if (args.isEmpty()) return Result.failure(IllegalArgumentException("Comando vacío"))
        return try {
            val command = arrayOf("su", "--", *args)
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val finished = process.waitFor(4, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return Result.failure(IllegalStateException("Timeout ejecutando root"))
            }
            if (process.exitValue() == 0) Result.success(output.trim())
            else Result.failure(IllegalStateException(output.ifBlank { "Comando root rechazado" }))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun applyDensity(dpi: Int): Result<String> {
        require(dpi in 360..650)
        return runRoot("wm", "density", dpi.toString())
    }

    fun applyAnimationScale(scale: Float): Result<String> {
        require(scale == 0f || scale == .5f)
        val value = scale.toString()
        return runRoot("settings", "put", "global", "window_animation_scale", value)
            .fold(
                onSuccess = { first ->
                    runRoot("settings", "put", "global", "transition_animation_scale", value)
                        .fold(
                            onSuccess = {
                                runRoot("settings", "put", "global", "animator_duration_scale", value)
                                    .map { "Animaciones aplicadas a ${scale}x" }
                            },
                            onFailure = { Result.failure(it) }
                        )
                },
                onFailure = { Result.failure(it) }
            )
    }
}
```

### Opt-in desde Compose

```kotlin
@Composable
fun RootModeCard(
    rootAvailable: Boolean,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(if (rootAvailable) "Root detectado" else "Root no disponible")
            Text("El modo root solo se ejecuta después de tu confirmación.")
            Switch(
                checked = enabled && rootAvailable,
                onCheckedChange = onEnabledChange,
                enabled = rootAvailable
            )
        }
    }
}
```

En `Activity.onCreate`, verifica `RootAccess.canExecuteRoot()` en `Dispatchers.IO`. Si el usuario habilitó el modo root en preferencias, aplica cambios en `onStart` o después de la confirmación, nunca durante una composición de Compose.

---

## 3. Foreground Service y HUD overlay

El FPS interno de otro proceso no es observable mediante una API pública. El siguiente HUD muestra **FPS estimados del propio overlay**, temperatura de batería y memoria disponible; no debe etiquetarse como FPS reales de Free Fire.

### `overlay/PerformanceOverlayService.kt`

```kotlin
package com.example.freefireboosterpro.overlay

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.freefireboosterpro.R
import java.util.Locale

class PerformanceOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var hud: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var frames = 0
    private var lastFrameNanos = System.nanoTime()
    private var measuredFps = 0

    private val updateTask = object : Runnable {
        override fun run() {
            frames++
            val now = System.nanoTime()
            val elapsed = now - lastFrameNanos
            if (elapsed >= 1_000_000_000L) {
                measuredFps = ((frames * 1_000_000_000L) / elapsed).toInt()
                frames = 0
                lastFrameNanos = now
            }
            hud?.text = buildHudText(measuredFps)
            handler.postDelayed(this, 250L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification())

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        hud = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xAA101010.toInt())
            setPadding(18, 10, 18, 10)
            textSize = 12f
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 120
        }

        windowManager.addView(hud, params)
        handler.post(updateTask)
    }

    private fun buildHudText(fps: Int): String {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val ram = memory.availMem / (1024 * 1024)
        val battery = getSystemService(BATTERY_SERVICE) as BatteryManager
        val tempIntent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tenths = tempIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val temperature = if (tenths != null && tenths != Int.MIN_VALUE) {
            String.format(Locale.US, "%.1f°C", tenths / 10f)
        } else {
            "N/D"
        }
        return "FPS est. $fps\nTemp. batería $temperature\nRAM libre ${ram} MB"
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hud?.let { view -> runCatching { windowManager.removeView(view) } }
        hud = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Performance HUD", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_name)
        .setContentTitle("Performance HUD activo")
        .setContentText("Métricas estimadas del dispositivo")
        .setOngoing(true)
        .build()

    companion object {
        private const val CHANNEL_ID = "performance_hud"
        private const val NOTIFICATION_ID = 1001
    }
}
```

Crea un icono de notificación válido en `res/drawable/ic_stat_name.xml` o sustituye `R.drawable.ic_stat_name` por un recurso existente. Para iniciar y detener el servicio:

```kotlin
fun Context.startPerformanceHud() {
    if (!Settings.canDrawOverlays(this)) return
    val intent = Intent(this, PerformanceOverlayService::class.java)
    androidx.core.content.ContextCompat.startForegroundService(this, intent)
}

fun Context.stopPerformanceHud() {
    stopService(Intent(this, PerformanceOverlayService::class.java))
}
```

No mantengas una referencia a `Activity`, `View` o `Compose state` dentro del servicio. El servicio crea y elimina su propia vista en `onCreate`/`onDestroy`, evitando fugas.

Para pedir el permiso overlay:

```kotlin
startActivity(
    Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        android.net.Uri.parse("package:$packageName")
    )
)
```

---

## 4. DNS y `VpnService`: arquitectura correcta

Un `VpnService` no cambia mágicamente el DNS del tráfico existente. Si se usa `addRoute("0.0.0.0", 0)`, todos los paquetes pasan al descriptor TUN y la app debe implementar un reenviador completo; si no, se corta la conectividad. Por eso no se debe distribuir un “VPN DNS” que solo cree la interfaz y declare `1.1.1.1`.

La alternativa segura para una primera versión es abrir la configuración de DNS privado y dejar que el usuario elija el proveedor:

```kotlin
fun Context.openPrivateDnsSettings() {
    val intent = Intent(Settings.ACTION_PRIVATE_DNS_SETTINGS)
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
```

Si se requiere un VPN real, usa una librería/proxy de red auditado que implemente:

1. Solicitud de consentimiento con `VpnService.prepare(context)`.
2. Un hilo de lectura/escritura del descriptor TUN.
3. Reenvío TCP/UDP y manejo de MTU.
4. DNS-over-TLS o DNS-over-HTTPS a un endpoint configurado.
5. Exclusión de la propia app y tratamiento de IPv6.
6. Kill switch y cierre limpio del descriptor en `onRevoke()`/`onDestroy()`.

Esqueleto deliberadamente no operativo para evitar romper la red:

```kotlin
class DnsVpnService : android.net.VpnService() {
    private var tun: java.io.FileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // No crear una ruta por defecto hasta implementar forwarding completo.
        // addDnsServer() por sí solo no intercepta consultas DNS de otras apps.
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        closeTun()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        closeTun()
        super.onDestroy()
    }

    private fun closeTun() {
        runCatching { tun?.let { java.io.FileInputStream(it).close() } }
        tun = null
    }
}
```

Para evitar confundir al usuario, la UI debe llamar a esta función `DNS privado`, no `reducción garantizada de ping`. El DNS puede mejorar la resolución inicial de dominios, pero normalmente no reduce el ping de los servidores de partida ni corrige jitter causado por Wi-Fi, ISP o ruta del operador.

---

## 5. Touch Boost y frecuencia de muestreo

Android no expone una API pública para forzar el polling rate táctil de una pantalla. `preferredRefreshRate` puede solicitar una frecuencia de refresco del display en una ventana propia, pero no cambia el sensor táctil ni el polling rate de Free Fire.

Código seguro para solicitar la mejor frecuencia de pantalla disponible en la propia Activity, sin prometer touch polling:

```kotlin
fun Activity.requestHighestDisplayRefreshRate() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return

    val display = display ?: return
    val mode = display.supportedModes.maxByOrNull { it.refreshRate } ?: return
    window.attributes = window.attributes.apply {
        preferredDisplayModeId = mode.modeId
        preferredRefreshRate = mode.refreshRate
    }
}
```

Llama a esta función desde `onCreate` o `onResume` de la Activity. Algunos fabricantes ofrecen APIs privadas, pero no son portables, pueden requerir permisos de sistema y no deben invocarse por reflexión como si fueran APIs oficiales.

---

## 6. Inicio seguro desde Compose

```kotlin
@Composable
fun AdvancedControls(activity: Activity) {
    val context = LocalContext.current
    var hudEnabled by rememberSaveable { mutableStateOf(false) }
    var rootOptIn by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = {
            if (Settings.canDrawOverlays(context)) {
                context.startPerformanceHud()
                hudEnabled = true
            } else {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        }) {
            Text(if (hudEnabled) "HUD activo" else "Activar HUD")
        }

        OutlinedButton(onClick = { context.stopPerformanceHud(); hudEnabled = false }) {
            Text("Detener HUD")
        }

        OutlinedButton(onClick = { context.openPrivateDnsSettings() }) {
            Text("Configurar DNS privado")
        }
    }
}
```

Para Android 13+, solicita notificaciones antes de `startForegroundService`. Para Android 14+, revisa el tipo de foreground service y su justificación. Detén siempre el servicio cuando el usuario desactive el HUD.

---

## Límites funcionales que deben reflejarse en la UI

- El root no se detecta con certeza absoluta y nunca debe activarse sin consentimiento.
- El HUD no puede medir los FPS internos de otro proceso con APIs públicas.
- La temperatura mostrada es la de batería, no necesariamente la del SoC.
- Un DNS alternativo no garantiza menor ping ni menor jitter.
- No hay API pública para forzar el touch polling rate.
- Ningún ajuste del sistema garantiza que un juego no aplique sus propias políticas antiabuso.
