package com.example.freefireboosterpro.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

data class ShizukuStatus(
    val available: Boolean = false,
    val permissionGranted: Boolean = false,
    val uid: Int? = null
) {
    val mode: String
        get() = when {
            !available -> "Shizuku no está activo"
            !permissionGranted -> "Permiso pendiente"
            uid == 0 -> "Conectado por root"
            uid == 2000 -> "Conectado por ADB"
            else -> "Conectado (UID $uid)"
        }
}

object ShizukuAccess {
    const val REQUEST_CODE = 7401

    fun status(): ShizukuStatus {
        val available = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!available) return ShizukuStatus()

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val uid = runCatching { Shizuku.getUid() }.getOrNull()
        return ShizukuStatus(available, granted, uid)
    }

    fun requestPermission() {
        if (status().available && !status().permissionGranted) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }
}
