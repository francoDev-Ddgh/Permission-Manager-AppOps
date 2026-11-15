package com.francodev.appops.shell

import android.content.Context
import android.content.pm.PackageManager
import com.francodev.appops.data.SettingsStore
import com.francodev.appops.data.WorkMode
import rikka.shizuku.Shizuku

enum class ShizukuStatus { NOT_RUNNING, NO_PERMISSION, READY }

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val ok: Boolean get() = exitCode == 0
}

object ShizukuShell {
    const val REQUEST_CODE = 1001

    private var initialized = false

    // Shizuku puede conectarse (binder) antes o después de que la app arranque.
    // El listener "sticky" avisa también si el binder ya estaba conectado, y entonces pide permiso.
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val store = SettingsStore(context.applicationContext)
        Shizuku.addBinderReceivedListenerSticky {
            if (store.getWorkMode() == WorkMode.SHIZUKU && status() == ShizukuStatus.NO_PERMISSION) {
                requestPermission()
            }
        }
    }

    fun status(): ShizukuStatus = try {
        when {
            !Shizuku.pingBinder() -> ShizukuStatus.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuStatus.NO_PERMISSION
            else -> ShizukuStatus.READY
        }
    } catch (e: Exception) {
        ShizukuStatus.NOT_RUNNING
    }

    // Shizuku solo muestra la app en su lista cuando la app pide permiso al menos una vez.
    // Si Shizuku no está conectado todavía, la llamada falla y se ignora.
    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    // Ejecuta un comando con privilegios de shell (ADB) a través de Shizuku.
    // Shizuku.newProcess es interno; se llama por reflexión.
    fun run(vararg command: String): ShellResult {
        val process = try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(null, arrayOf(*command), null, null) as Process
        } catch (e: Exception) {
            return ShellResult(-1, "", e.message ?: "No se pudo crear el proceso con Shizuku")
        }
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        return ShellResult(code, stdout.trim(), stderr.trim())
    }
}
