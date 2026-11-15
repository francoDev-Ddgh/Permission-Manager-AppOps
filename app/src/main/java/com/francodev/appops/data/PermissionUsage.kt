package com.francodev.appops.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.francodev.appops.shell.ShizukuShell
import com.francodev.appops.shell.ShizukuStatus
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
 * Permiso del catálogo que usa una app concreta, con su estado.
 */
data class AppPermissionEntry(
    val permission: ManagedPermission,
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

private const val FLAG_SUPPORTS_PICTURE_IN_PICTURE = 0x400000

/**
 * Devuelve las apps instaladas que declaran el permiso indicado y si lo tienen activo.
 */
suspend fun loadAppsUsingPermission(
    context: Context,
    permission: ManagedPermission
): List<AppPermissionState> = withContext(Dispatchers.IO) {
    val candidates = when (permission.source) {
        PermissionSource.PIP_ACTIVITY -> pipCapableApps(context)
        PermissionSource.PERMISSION -> appsDeclaring(context, permission)
    }

    val enabled = enabledComponentsFor(permission)

    candidates.map { app ->
        val active = if (enabled != null) {
            enabled.any { it.startsWith("${app.packageName}/") }
        } else {
            readState(context, app.packageName, permission)
        }
        AppPermissionState(app = app, active = active)
    }
}

/**
 * Permisos administrables que usa una app, listos para mostrar con un interruptor.
 */
suspend fun loadPermissionsForApp(
    context: Context,
    packageName: String
): List<AppPermissionEntry> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val pkg = runCatching {
        pm.getPackageInfo(
            packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES
        )
    }.getOrNull() ?: return@withContext emptyList()

    val requested = pkg.requestedPermissions?.toSet().orEmpty()
    val supportsPip = pkg.activities?.any { activity ->
        (activity.flags and FLAG_SUPPORTS_PICTURE_IN_PICTURE) != 0
    } == true

    managedPermissions.mapNotNull { permission ->
        val uses = when (permission.source) {
            PermissionSource.PIP_ACTIVITY -> supportsPip
            PermissionSource.PERMISSION -> permission.manifestPermission
                ?.let { "android.permission.$it" in requested } == true
        }
        if (!uses) return@mapNotNull null

        val enabled = enabledComponentsFor(permission)
        val active = if (enabled != null) {
            enabled.any { it.startsWith("$packageName/") }
        } else {
            readState(context, packageName, permission)
        }
        AppPermissionEntry(permission = permission, active = active)
    }
}

/**
 * Apps que declaran el permiso en su manifiesto.
 */
private fun appsDeclaring(context: Context, permission: ManagedPermission): List<UserApp> {
    val declared = permission.manifestPermission ?: return emptyList()
    val fullPermission = "android.permission.$declared"
    val pm = context.packageManager

    return pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
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
}

/**
 * Apps con alguna actividad marcada con `android:supportsPictureInPicture="true"`.
 * El flag es 0x400000 (ActivityInfo.FLAG_SUPPORTS_PICTURE_IN_PICTURE), que no está
 * expuesto en los stubs públicos del SDK.
 */
private fun pipCapableApps(context: Context): List<UserApp> {
    val pm = context.packageManager

    return pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)
        .asSequence()
        .filter { it.packageName != context.packageName }
        .filter { pkg ->
            pkg.activities?.any { activity ->
                (activity.flags and FLAG_SUPPORTS_PICTURE_IN_PICTURE) != 0
            } == true
        }
        .mapNotNull { pkg ->
            pkg.applicationInfo?.let { info ->
                UserApp(packageName = pkg.packageName, label = pm.getApplicationLabel(info).toString())
            }
        }
        .sortedBy { it.label.lowercase() }
        .toList()
}

/**
 * Para accesibilidad y acceso a notificaciones el estado vive en Settings.Secure.
 */
private fun enabledComponentsFor(permission: ManagedPermission): Set<String>? =
    when (permission.name) {
        "BIND_ACCESSIBILITY_SERVICE" -> enabledComponents("enabled_accessibility_services")
        "BIND_NOTIFICATION_LISTENER_SERVICE" -> enabledComponents("enabled_notification_listeners")
        else -> null
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
): Boolean {
    val declared = permission.manifestPermission ?: permission.name
    return context.packageManager.checkPermission(
        "android.permission.$declared",
        packageName
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Consulta el modo de un app op. Si la operación no existe en esta ROM, se prueba con
 * SEND_SMS (SMS Premium) y, como último recurso, con el permiso concedido.
 */
private fun readAppOpsState(
    context: Context,
    packageName: String,
    permission: ManagedPermission
): Boolean {
    readOpMode(packageName, appOpsName(permission.name))?.let { return it }

    if (permission.name == "SMS_PREMIUM") {
        readOpMode(packageName, "SEND_SMS")?.let { return it }
    }

    return isPermissionGranted(context, packageName, permission)
}

/**
 * Interpreta la salida de `appops get <paquete> <op>` buscando SOLO la línea de esa
 * operación. Algunas ROMs imprimen una lista completa cuando no conocen el nombre,
 * y mirar todo el texto daba falsos negativos.
 *
 * Modos posibles: allow, foreground, default, ignore, deny, erase.
 * Devuelve null si no hay un modo interpretable.
 */
private fun readOpMode(packageName: String, op: String): Boolean? {
    val result = ShizukuShell.run("appops", "get", packageName, op)
    if (!result.ok) return null

    val line = result.stdout.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .firstOrNull { it.substringBefore(':').trim().equals(op, ignoreCase = true) }
        ?: return null

    val mode = line.substringAfter(':').substringBefore(';').trim().lowercase()
    return when {
        mode.startsWith("allow") -> true
        mode.startsWith("foreground") -> true
        mode.startsWith("ignore") -> false
        mode.startsWith("deny") -> false
        mode.startsWith("erase") -> false
        else -> null
    }
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
 *
 * Antes de ejecutar comprueba que Shizuku esté listo. Después vuelve a leer el estado
 * y, si el sistema no refleja el cambio, devuelve error para que la UI no muestre un
 * valor falso.
 */
suspend fun setPermissionState(
    context: Context,
    packageName: String,
    permission: ManagedPermission,
    active: Boolean
): ToggleResult = withContext(Dispatchers.IO) {
    val strategy = strategyOf(permission)

    if (strategy == Strategy.SETTINGS) {
        return@withContext ToggleResult(
            success = false,
            message = "Este permiso solo se puede cambiar desde Ajustes del sistema."
        )
    }

    if (ShizukuShell.status() != ShizukuStatus.READY) {
        return@withContext ToggleResult(
            success = false,
            message = "Shizuku no está conectado o la app no tiene permiso."
        )
    }

    val outcome = when (strategy) {
        Strategy.APPOPS -> runAppOpsSet(packageName, permission, active)
        Strategy.PERMISSION -> {
            val declared = permission.manifestPermission ?: permission.name
            val action = if (active) "grant" else "revoke"
            SetOutcome(
                ShizukuShell.run("pm", action, packageName, "android.permission.$declared")
            )
        }
        Strategy.NETWORK -> {
            // La cadena de firewall debe estar activa para que la regla haga efecto.
            if (!active) {
                ShizukuShell.run("cmd", "connectivity", "set-chain3-enabled", "true")
            }
            SetOutcome(
                ShizukuShell.run(
                    "cmd", "connectivity", "set-package-networking-enabled",
                    active.toString(), packageName
                )
            )
        }
        Strategy.SETTINGS -> return@withContext ToggleResult(false)
    }

    val result = outcome.result

    if (!result.ok) {
        return@withContext ToggleResult(
            success = false,
            message = "El comando falló: ${result.describe()}"
        )
    }

    // El firewall por app no se puede leer de forma fiable en todas las versiones,
    // así que solo se verifica app ops y permisos.
    if (strategy == Strategy.APPOPS || strategy == Strategy.PERMISSION) {
        val now = readState(context, packageName, permission)
        if (now != active) {
            return@withContext ToggleResult(
                success = false,
                message = "El sistema no aplicó el cambio: sigue " +
                    "${if (now) "activo" else "inactivo"}. ${result.describe()}"
            )
        }
    }

    ToggleResult(
        success = true,
        message = if (outcome.usedFallback) {
            "Esta ROM no tiene app op de SMS Premium; el cambio se aplicó al envío de SMS."
        } else {
            null
        }
    )
}

private data class SetOutcome(val result: ShellResult, val usedFallback: Boolean = false)

private fun runAppOpsSet(
    packageName: String,
    permission: ManagedPermission,
    active: Boolean
): SetOutcome {
    val mode = if (active) "allow" else "ignore"
    val primary = ShizukuShell.run(
        "appops", "set", packageName, appOpsName(permission.name), mode
    )
    if (primary.ok || permission.name != "SMS_PREMIUM") return SetOutcome(primary)

    // Muchas ROMs no traen el app op SMS_PREMIUM. El envío de SMS es lo más
    // cercano que existe, así que se controla con SEND_SMS.
    val fallback = ShizukuShell.run("appops", "set", packageName, "SEND_SMS", mode)
    return SetOutcome(fallback, usedFallback = fallback.ok)
}

private fun ShellResult.describe(): String {
    val text = listOf(stderr, stdout)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .trim()
    return text.ifBlank { "sin salida del comando" }
}

/**
 * El nombre del app op no siempre coincide con el del permiso.
 */
private fun appOpsName(permission: String): String = when (permission) {
    "POST_NOTIFICATIONS" -> "POST_NOTIFICATION"
    else -> permission
}