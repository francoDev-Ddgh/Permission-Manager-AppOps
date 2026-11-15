package com.francodev.appops.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.francodev.appops.data.SettingsStore
import com.francodev.appops.data.WorkMode
import rikka.shizuku.Shizuku
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

enum class ShizukuStatus { NOT_RUNNING, NO_PERMISSION, READY }

/**
 * Gestiona la ejecución de comandos con privilegios a través de Shizuku.
 *
 * Desde Shizuku API 13.1.5 el método `Shizuku.newProcess` dejó de existir, por lo que
 * el camino principal es un UserService con AIDL. El método antiguo por reflexión se
 * mantiene solo como fallback para versiones anteriores de Shizuku.
 */
object ShizukuShell {
    const val REQUEST_CODE = 1001

    private const val TAG = "ShizukuShell"
    private const val USER_SERVICE_TAG = "shell"
    private const val USER_SERVICE_VERSION = 1
    private const val BIND_TIMEOUT_SECONDS = 5L

    private var initialized = false
    private var appContext: Context? = null

    private var shellService: IShellService? = null
    private var serviceConnection: ServiceConnection? = null
    private var userServiceArgs: Shizuku.UserServiceArgs? = null

    // Caché del método newProcess (solo existe en Shizuku < 13.1.5)
    private var newProcessMethod: Method? = null
    private var newProcessChecked = false

    /**
     * Guarda el contexto de la aplicación, necesario para vincular el UserService.
     */
    private fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Inicializa Shizuku. Debe llamarse una vez al inicio de la app.
     */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        setContext(context)
        val store = SettingsStore(context.applicationContext)

        Shizuku.addBinderReceivedListenerSticky {
            if (store.getWorkMode() == WorkMode.SHIZUKU &&
                status() == ShizukuStatus.NO_PERMISSION
            ) {
                requestPermission()
            }
        }
    }

    /**
     * Vincula el UserService. Es idempotente: si ya está vinculado devuelve true.
     */
    @Synchronized
    fun bindUserService(): Boolean {
        if (shellService != null) return true

        val context = appContext ?: run {
            Log.w(TAG, "Cannot bind UserService: no app context")
            return false
        }
        if (status() != ShizukuStatus.READY) {
            Log.w(TAG, "Cannot bind UserService: Shizuku not ready")
            return false
        }

        val component = ComponentName(context.packageName, ShellUserService::class.java.name)
        val args = Shizuku.UserServiceArgs(component)
            .daemon(false)
            .debuggable(false)
            .tag(USER_SERVICE_TAG)
            .version(USER_SERVICE_VERSION)

        val latch = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                shellService = IShellService.Stub.asInterface(binder)
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(TAG, "UserService disconnected")
                shellService = null
            }
        }

        userServiceArgs = args
        serviceConnection = connection

        return try {
            Shizuku.bindUserService(args, connection)
            val connected = latch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!connected) Log.w(TAG, "Timed out binding UserService")
            connected && shellService != null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind UserService", e)
            shellService = null
            false
        }
    }

    /**
     * Desvincula el UserService y pide al servidor que lo elimine.
     */
    @Synchronized
    fun unbindUserService() {
        val args = userServiceArgs
        val connection = serviceConnection
        if (args != null && connection != null) {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
                .onFailure { Log.e(TAG, "Failed to unbind UserService", it) }
        }
        shellService = null
        serviceConnection = null
        userServiceArgs = null
    }

    /**
     * Versión de Shizuku instalada, o null si no se puede determinar.
     */
    fun getShizukuVersion(): Int? = runCatching { Shizuku.getVersion() }.getOrNull()

    private fun getNewProcessMethod(): Method? {
        if (!newProcessChecked) {
            newProcessChecked = true
            newProcessMethod = runCatching {
                Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }
            }.getOrNull()
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
     * Solicita permiso de Shizuku. Si el binder no está listo la llamada falla y se ignora.
     */
    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /**
     * Ejecuta un comando con privilegios de shell (ADB) o root a través de Shizuku.
     *
     * Intenta primero con el UserService y, si no está disponible, con el método
     * legacy por reflexión (Shizuku < 13.1.5).
     */
    fun run(vararg command: String): ShellResult {
        if (!bindUserService()) {
            return runLegacy(*command)
        }

        return try {
            shellService?.executeCommand(command) ?: runLegacy(*command)
        } catch (e: Exception) {
            Log.e(TAG, "UserService call failed, falling back to legacy", e)
            shellService = null
            runLegacy(*command)
        }
    }

    /**
     * Fallback para Shizuku < 13.1.5: usa `Shizuku.newProcess` por reflexión.
     */
    private fun runLegacy(vararg command: String): ShellResult {
        val method = getNewProcessMethod()
            ?: return ShellResult(
                -1,
                "",
                "Shizuku ${getShizukuVersion()?.let { "v$it" } ?: "(versión desconocida)"} no expone " +
                    "newProcess y el UserService no se pudo vincular. " +
                    "Comprueba que Shizuku está en ejecución y que la app tiene permiso."
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
}