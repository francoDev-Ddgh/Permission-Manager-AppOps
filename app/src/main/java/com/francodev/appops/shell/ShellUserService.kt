package com.francodev.appops.shell

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * UserService que ejecuta comandos con el UID de shell (2000) o root (0), según
 * cómo esté ejecutándose Shizuku.
 *
 * Vive en un proceso aparte y se vincula desde [ShizukuShell] con
 * `Shizuku.bindUserService`.
 */
class ShellUserService : IShellService.Stub() {

    override fun executeCommand(command: Array<out String>): ShellResult {
        return try {
            val process = ProcessBuilder(*command).start()
            val stdout = process.inputStream.bufferedReader()
                .use(BufferedReader::readText)
            val stderr = process.errorStream.bufferedReader()
                .use(BufferedReader::readText)
            val exitCode = process.waitFor()
            ShellResult(exitCode, stdout.trim(), stderr.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando: ${command.joinToString(" ")}", e)
            ShellResult(-1, "", e.message ?: "Error desconocido")
        }
    }

    /**
     * Shizuku llama a este método (transacción 16777115) cuando el servicio debe morir.
     * El proceso no se detiene solo, hay que salir explícitamente.
     */
    override fun destroy() {
        Log.d(TAG, "destroy")
        System.exit(0)
    }

    private companion object {
        const val TAG = "ShellUserService"
    }
}