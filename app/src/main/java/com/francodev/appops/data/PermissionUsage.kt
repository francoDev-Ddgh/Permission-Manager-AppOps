package com.francodev.appops.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.francodev.appops.shell.ShizukuShell
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
 * Devuelve las apps instaladas que declaran el permiso indicado y si lo tienen activo.
 *
 * La lista de candidatas sale del manifiesto (no hace falta Shizuku para eso). El estado
 * se consulta con Shizuku: `appops` para los permisos normales y `settings get secure`
 * para accesibilidad y acceso a notificaciones, que no son app ops.
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
            resolveAppOpsState(context, app.packageName, permission.name)
        }
        AppPermissionState(app = app, active = active)
    }
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

/**
 * Consulta el modo de un app op. Si Shizuku no está disponible o la operación no existe,
 * se usa el permiso concedido según PackageManager.
 */
private fun resolveAppOpsState(
    context: Context,
    packageName: String,
    permission: String
): Boolean {
    val result = ShizukuShell.run("appops", "get", packageName, appOpsName(permission))
    if (result.ok) {
        val output = result.stdout.lowercase()
        if ("ignore" in output || "deny" in output) return false
        if ("allow" in output) return true
    }

    return context.packageManager
        .checkPermission("android.permission.$permission", packageName) ==
        PackageManager.PERMISSION_GRANTED
}

/**
 * El nombre del app op no siempre coincide con el del permiso.
 */
private fun appOpsName(permission: String): String = when (permission) {
    "POST_NOTIFICATIONS" -> "POST_NOTIFICATION"
    else -> permission
}

/**
 * Accesibilidad y acceso a notificaciones no son app ops: se activan desde Ajustes del
 * sistema, así que de momento no se pueden cambiar desde aquí.
 */
fun canTogglePermission(permission: ManagedPermission): Boolean =
    permission.name != "BIND_ACCESSIBILITY_SERVICE" &&
        permission.name != "BIND_NOTIFICATION_LISTENER_SERVICE"

/**
 * Concede o revoca el permiso para una app usando `appops set`.
 *
 * @return true si el comando terminó correctamente.
 */
suspend fun setPermissionState(
    packageName: String,
    permission: ManagedPermission,
    active: Boolean
): Boolean = withContext(Dispatchers.IO) {
    if (!canTogglePermission(permission)) return@withContext false

    val mode = if (active) "allow" else "ignore"
    val result = ShizukuShell.run(
        "appops", "set", packageName, appOpsName(permission.name), mode
    )
    result.ok
}