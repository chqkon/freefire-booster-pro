package com.example.freefireboosterpro.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.example.freefireboosterpro.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

object ShizukuCommandRunner {
    private const val BIND_TIMEOUT_MS = 4_000L

    suspend fun execute(command: Array<String>): CommandResult = withContext(Dispatchers.IO) {
        val status = ShizukuAccess.status()
        if (!status.available) return@withContext CommandResult(-2, "Shizuku no está activo")
        if (!status.permissionGranted) return@withContext CommandResult(-3, "Permiso Shizuku pendiente")

        val connected = CompletableDeferred<IShizukuCommandService?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (!connected.isCompleted) {
                    connected.complete(IShizukuCommandService.Stub.asInterface(binder))
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (!connected.isCompleted) connected.complete(null)
            }
        }
        val args = Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, ShizukuCommandService::class.java.name)
        ).daemon(false)
            .processNameSuffix("command")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        try {
            Shizuku.bindUserService(args, connection)
            val service = withTimeout(BIND_TIMEOUT_MS) { connected.await() }
                ?: return@withContext CommandResult(-4, "No se pudo enlazar el UserService")
            val encoded = service.execute(command)
            val split = encoded.indexOf('\n')
            val exitCode = if (split >= 0) encoded.substring(0, split).toIntOrNull() ?: -1 else -1
            val output = if (split >= 0) encoded.substring(split + 1) else encoded
            CommandResult(exitCode, output)
        } catch (error: RemoteException) {
            CommandResult(-5, error.message.orEmpty())
        } catch (error: Exception) {
            CommandResult(-6, error.message.orEmpty())
        } finally {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
        }
    }

    data class CommandResult(val exitCode: Int, val output: String)
}
