package com.francodev.appops.shell

import android.os.IBinder
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * UserService que ejecuta comandos shell con privilegios de ADB/root.
 *
 * Este servicio corre en un proceso separado con el UID de shell (2000) o root (0),
 * dependiendo de cómo esté configurado Shizuku.
 *
 * Uso: Se vincula mediante Shizuku.bindUserService() desde ShizukuShell.
 */
class ShellUserService : IShellService.Stub() {

    override fun executeCommand(command: Array<String>): ShellResult {
        return try {
            val process = ProcessBuilder(*command).start()
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            val exitCode = process.waitFor()
            ShellResult(exitCode, stdout.trim(), stderr.trim())
        } catch (e: Exception) {
            Log.e("ShellUserService", "Error executing command: ${command.joinToString(" ")}", e)
            ShellResult(-1, "", e.message ?: "Error desconocido")
        }
    }

    /**
     * Método llamado por Shizuku para destruir el servicio.
     * Transaction code 16777115 según documentación de Shizuku.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d("ShellUserService", "ShellUserService destroyed")
        System.exit(0)
    }

    companion object {
        private const val TAG = "ShellUserService"
    }
}