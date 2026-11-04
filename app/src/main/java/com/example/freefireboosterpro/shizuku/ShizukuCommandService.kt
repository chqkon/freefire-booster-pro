package com.example.freefireboosterpro.shizuku

import androidx.annotation.Keep
import com.example.freefireboosterpro.shizuku.IShizukuCommandService

/**
 * Vive en el proceso autorizado por Shizuku. No se expone como un Service Android
 * normal; Shizuku crea esta clase mediante UserService.
 */
class ShizukuCommandService() : IShizukuCommandService.Stub() {

    override fun execute(command: Array<String>): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val stdout = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = process.waitFor()
            val output = if (stderr.isNotBlank()) stderr else stdout
            "$exitCode\n$output"
        } catch (error: Exception) {
            "-1\n${error.message.orEmpty()}"
        }
    }

    override fun destroy() {
        System.exit(0)
    }
}
