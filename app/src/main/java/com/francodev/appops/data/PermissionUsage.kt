package com.francodev.appops.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.francodev.appops.shell.ShizukuShell
import com.francodev.appops.shell.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Estado de un permiso para una app concreta.
 */
data class AppPermissionState(
    val app: UserApp,
    val active: Boolean
)

/**
 * Resultado de intentar cambiar un permiso.
 */
data class ToggleResult(
    val success: Boolean,
    val message: String? = null
)

/**
 * Cada permiso se lee y se cambia de una forma distinta.
 */
private enum class Strategy {
    /** App ops: `appops get/set`. La mayoría de permisos. */
    APPOPS,

    /** Permiso normal: `pm grant/revoke` y `checkPermission`. */
    PERMISSION,

    /** Acceso a red por app: `cmd connectivity`. */
    NETWORK,

    /** Se activa solo desde Ajustes del sistema (accesibilidad, notificaciones). */
    SETTINGS
}

private fun strategyOf(permission: ManagedPermission): Strategy = when (permission.name) {
    "INTERNET" -> Strategy.NETWORK
    "WRITE_SECURE_SETTINGS" -> Strategy.PERMISSION
    "BIND_ACCESSIBILITY_SERVICE", "BIND_NOTIFICATION_LISTENER_SERVICE" -> Strategy.SETTINGS
    else -> Strategy.APPOPS
}

fun canTogglePermission(permission: ManagedPermission): Boolean =
    strategyOf(permission) != Strategy.SETTINGS

/**
 * Devuelve las apps instaladas que declaran el permiso indicado y si lo tienen activo.
 *
 * Las candidatas salen del manifiesto, así que no hace falta Shizuku para listarlas.
 */
suspend fun loadAppsUsingPermission(
    context: Context,
    permission: ManagedPermission
): List<AppPermissionState> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val fullPermission = "android.permission.${permission.name}"

    val candidates = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        .asSequence()
        .mapNotNull { pkg -> pkg.applicationInfo?.let { pkg to it } }
        .filter { (_, info) -> (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
        .filter { (pkg, _) -> pkg.packageName != context.packageName }
        .filter { (pkg, _) -> pkg.requestedPermissions?.contains(fullPermission) == true }
        .map { (pkg, info) ->
            UserApp(packageName = pkg.packageName, label = pm.getApplicationLabel(info).toString())
        }
        .sortedBy { it.label.lowercase() }
        .toList()

    // Estos dos permisos se conceden desde Ajustes y se leen de Settings.Secure,
    // así que se resuelven con una sola consulta en lugar de una por app.
    val enabledComponents = when (permission.name) {
        "BIND_ACCESSIBILITY_SERVICE" -> enabledComponents("enabled_accessibility_services")
        "BIND_NOTIFICATION_LISTENER_SERVICE" -> enabledComponents("enabled_notification_listeners")
        else -> null
    }

    candidates.map { app ->
        val active = if (enabledComponents != null) {
            enabledComponents.any { it.startsWith("${app.packageName}/") }
        } else {
            readState(context, app.packageName, permission)
        }
        AppPermissionState(app = app, active = active)
    }
}

private fun readState(
    context: Context,
    packageName: String,
    permission: ManagedPermission
): Boolean = when (strategyOf(permission)) {
    Strategy.NETWORK -> readNetworkState(context, packageName, permission)
    Strategy.PERMISSION -> isPermissionGranted(context, packageName, permission)
    Strategy.SETTINGS -> false
    Strategy.APPOPS -> readAppOpsState(context, packageName, permission)
}

/**
 * Lee una lista de componentes habilitados de Settings.Secure.
 * El formato es "pkg/clase:pkg2/clase2", de ahí el split por ':'.
 */
private fun enabledComponents(setting: String): Set<String> {
    val result = ShizukuShell.run("settings", "get", "secure", setting)
    if (!result.ok) return emptySet()
    return result.stdout
        .split(':')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}

private fun isPermissionGranted(
    context: Context,
    packageName: String,
    permission: ManagedPermission
): Boolean = context.packageManager.checkPermission(
    "android.permission.${permission.name}",
    packageName
) == PackageManager.PERMISSION_GRANTED

/**
 * Consulta el modo de un app op. Si la operación no existe (por ejemplo INTERNET),
 * se usa el permiso concedido según PackageManager.
 */
private fun readAppOpsState(
    context: Context,
    packageName: String,
    permission: ManagedPermission
): Boolean {
    val result = ShizukuShell.run("appops", "get", packageName, appOpsName(permission.name))
    if (result.ok) {
        val output = result.stdout.lowercase()
        if ("ignore" in output || "deny" in output) return false
        if ("allow" in output) return true
    }

    return isPermissionGranted(context, packageName, permission)
}

/**
 * Estado del firewall por app. `get-package-networking-enabled` responde
 * "<paquete>:allow" o "<paquete>:deny" (Android 13+).
 */
private fun readNetworkState(
    context: Context,
    packageName: String,
    permission: ManagedPermission
): Boolean {
    val result = ShizukuShell.run(
        "cmd", "connectivity", "get-package-networking-enabled", packageName
    )
    if (result.ok) {
        when (result.stdout.substringAfterLast(':').trim().lowercase()) {
            "allow" -> return true
            "deny" -> return false
        }
    }

    return isPermissionGranted(context, packageName, permission)
}

/**
 * Concede o revoca el permiso para una app.
 */
suspend fun setPermissionState(
    packageName: String,
    permission: ManagedPermission,
    active: Boolean
): ToggleResult = withContext(Dispatchers.IO) {
    when (strategyOf(permission)) {
        Strategy.SETTINGS -> ToggleResult(
            success = false,
            message = "Este permiso solo se puede cambiar desde Ajustes del sistema."
        )

        Strategy.APPOPS -> {
            val mode = if (active) "allow" else "ignore"
            val result = ShizukuShell.run(
                "appops", "set", packageName, appOpsName(permission.name), mode
            )
            result.toToggleResult()
        }

        Strategy.PERMISSION -> {
            val action = if (active) "grant" else "revoke"
            val result = ShizukuShell.run(
                "pm", action, packageName, "android.permission.${permission.name}"
            )
            result.toToggleResult()
        }

        Strategy.NETWORK -> {
            // La cadena de firewall debe estar activa para que la regla haga efecto.
            if (!active) {
                ShizukuShell.run("cmd", "connectivity", "set-chain3-enabled", "true")
            }
            val result = ShizukuShell.run(
                "cmd", "connectivity", "set-package-networking-enabled",
                active.toString(), packageName
            )
            result.toToggleResult()
        }
    }
}

private fun ShellResult.toToggleResult(): ToggleResult =
    if (ok) {
        ToggleResult(success = true)
    } else {
        ToggleResult(
            success = false,
            message = stderr.ifBlank { stdout }.ifBlank { "El comando falló." }
        )
    }

/**
 * El nombre del app op no siempre coincide con el del permiso.
 */
private fun appOpsName(permission: String): String = when (permission) {
    "POST_NOTIFICATIONS" -> "POST_NOTIFICATION"
    else -> permission
}