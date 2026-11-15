package com.francodev.appops.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.francodev.appops.data.SettingsStore
import com.francodev.appops.data.WorkMode
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

enum class ShizukuStatus { NOT_RUNNING, NO_PERMISSION, READY }

/**
 * Gestiona la ejecución de comandos con privilegios a través de Shizuku.
 *
 * Funciona con Shizuku >= 13.1.5 usando UserService (método recomendado).
 * Para versiones anteriores, usa reflexión al método interno newProcess.
 */
object ShizukuShell {
    const val REQUEST_CODE = 1001

    private var initialized = false

    // UserService connection
    private var shellService: IShellService? = null
    private var serviceConnection: ServiceConnection? = null
    private var userServiceArgs: Shizuku.UserServiceArgs? = null
    private val serviceLatch = CountDownLatch(1)

    // Caché del método newProcess (fallback para Shizuku < 13.1.5)
    private var newProcessMethod: Method? = null
    private var newProcessChecked = false

    /**
     * Inicializa Shizuku. Debe llamarse una vez al inicio de la app.
     */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val store = SettingsStore(context.applicationContext)

        // Registrar listener para cuando el binder esté disponible
        Shizuku.addBinderReceivedListenerSticky {
            if (store.getWorkMode() == WorkMode.SHIZUKU && status() == ShizukuStatus.NO_PERMISSION) {
                requestPermission()
            }
        }
    }

    /**
     * Vincula el UserService con Shizuku.
     * Debe llamarse cuando Shizuku esté listo (status == READY).
     *
     * @param context Contexto de la aplicación
     * @return true si el servicio se vinculó correctamente
     */
    fun bindUserService(context: Context): Boolean {
        if (shellService != null) return true
        if (status() != ShizukuStatus.READY) {
            Log.w("ShizukuShell", "Cannot bind UserService: Shizuku not ready")
            return false
        }

        val intent = Intent().apply {
            component = ComponentName(
                context.packageName,
                ShellUserService::class.java.name
            )
        }

        val args = Shizuku.UserServiceArgs(intent).apply {
            daemon = false
            debuggable = false
            version = 1
        }

        userServiceArgs = args
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.d("ShizukuShell", "UserService connected")
                shellService = IShellService.Stub.asInterface(service)
                serviceLatch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d("ShizukuShell", "UserService disconnected")
                shellService = null
            }
        }

        val result = try {
            Shizuku.bindUserService(args, serviceConnection)
            serviceLatch.await(5, TimeUnit.SECONDS)
            shellService != null
        } catch (e: Exception) {
            Log.e("ShizukuShell", "Failed to bind UserService", e)
            false
        }

        return result
    }

    /**
     * Desvincula el UserService.
     */
    fun unbindUserService() {
        val args = userServiceArgs ?: return
        val connection = serviceConnection ?: return
        try {
            Shizuku.unbindUserService(args, connection, true)
        } catch (e: Exception) {
            Log.e("ShizukuShell", "Failed to unbind UserService", e)
        }
        shellService = null
        serviceConnection = null
        userServiceArgs = null
    }

    /**
     * Verifica si el método legacy newProcess está disponible (Shizuku < 13.1.5).
     */
    fun isLegacyNewProcessAvailable(): Boolean = getNewProcessMethod() != null

    /**
     * Devuelve la versión de Shizuku instalada, o null si no se puede determinar.
     */
    fun getShizukuVersion(): Int? = try {
        Shizuku.getVersion()
    } catch (e: Exception) {
        null
    }

    /**
     * Obtiene el método newProcess por reflexión (solo para fallback).
     */
    private fun getNewProcessMethod(): Method? {
        if (!newProcessChecked) {
            newProcessChecked = true
            newProcessMethod = try {
                Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }
            } catch (e: NoSuchMethodException) {
                null
            }
        }
        return newProcessMethod
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

    /**
     * Solicita permiso de Shizuku.
     */
    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /**
     * Ejecuta un comando con privilegios de shell (ADB) a través de Shizuku.
     *
     * @param command Comando y argumentos a ejecutar
     * @return ShellResult con exitCode, stdout y stderr
     */
    fun run(vararg command: String): ShellResult {
        // Intentar con UserService primero (Shizuku >= 13.1.5)
        if (shellService == null) {
            // Si no está vinculado, intentar vincular ahora
            val context = getContext()
            if (context != null && bindUserService(context)) {
                // Servicio vinculado exitosamente
            } else {
                // Fallback al método legacy
                return runLegacy(vararg command)
            }
        }

        // Ejecutar con UserService
        return try {
            shellService?.executeCommand(command) ?: runLegacy(vararg command)
        } catch (e: Exception) {
            Log.e("ShizukuShell", "Error with UserService, trying legacy", e)
            runLegacy(vararg command)
        }
    }

    /**
     * Fallback: ejecuta usando el método legacy newProcess por reflexión.
     */
    private fun runLegacy(vararg command: String): ShellResult {
        val method = getNewProcessMethod()
            ?: return ShellResult(
                -1,
                "",
                "newProcess no está disponible en tu versión de Shizuku " +
                    "(${getShizukuVersion()?.let { "v$it" } ?: "desconocida"}). " +
                    "Shizuku >= 13.1.5 eliminó newProcess. Se necesita UserService."
            )

        val process = try {
            method.invoke(null, arrayOf(*command), null, null) as Process
        } catch (e: Exception) {
            return ShellResult(-1, "", e.message ?: "No se pudo crear el proceso con Shizuku")
        }
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        return ShellResult(code, stdout.trim(), stderr.trim())
    }

    // Contexto global para UserService
    private var appContext: Context? = null

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    private fun getContext(): Context? = appContext
}