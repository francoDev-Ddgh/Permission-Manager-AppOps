# Permission-Manager-AppOps - Integración con Shizuku

## Problema

La versión anterior del código intentaba usar `Shizuku.newProcess()` por reflexión, pero este método fue **eliminado en Shizuku API 13.1.5+**. Esto causaba que la app fallara silenciosamente en dispositivos con versiones modernas de Shizuku.

## Solución implementada

Se ha migrado a **UserService** (método recomendado por Shizuku), con fallback automático al método legacy para compatibilidad hacia atrás.

### Archivos modificados/creados:

1. **`IShellService.aidl`** - Interfaz AIDL para comunicación con el UserService
2. **`ShellResult.kt`** - Clase Parcelable para transportar resultados
3. **`ShellUserService.kt`** - Implementación del servicio que ejecuta comandos
4. **`ShizukuShell.kt`** - Gestión del ciclo de vida del servicio y ejecución de comandos
5. **`MainActivity.kt`** - Inicialización del contexto para UserService

### Funcionamiento:

```
App → ShizukuShell.run("cmd", "pm", "list", "packages")
         ↓
    ¿UserService vinculado? → Sí → Ejecutar con UserService
         ↓ No
    ¿newProcess disponible? → Sí → Ejecutar con reflexión (legacy)
         ↓ No
    Error: Shizuku >= 13.1.5 requiere UserService
```

## Uso

### Inicialización (MainActivity):

```kotlin
ShizukuShell.setContext(this)
ShizukuShell.init(this)
```

### Verificar estado:

```kotlin
val status = ShizukuShell.status()  // NOT_RUNNING, NO_PERMISSION, READY
```

### Ejecutar comandos:

```kotlin
val result = ShizukuShell.run("pm", "list", "packages")
if (result.ok) {
    Log.d("AppOps", "Output: ${result.stdout}")
} else {
    Log.e("AppOps", "Error: ${result.stderr}")
}
```

### Verificar capacidades:

```kotlin
// ¿newProcess disponible? (false en Shizuku >= 13.1.5)
val hasLegacy = ShizukuShell.isLegacyNewProcessAvailable()

// Versión de Shizuku
val version = ShizukuShell.getShizukuVersion()
```

## Requisitos

- Shizuku instalado y en ejecución
- Permiso `moe.shizuku.manager.permission.API_V23` concedido
- Android 6.0+ (API 26+ según minSdk del proyecto)

## Notas técnicas

- **UserService** corre en un proceso separado con UID de shell (2000) o root (0)
- El servicio se vincula automáticamente al primer uso si Shizuku está listo
- Fallback automático a reflexión si UserService falla (para Shizuku < 13.1.5)
- El método legacy usa `Shizuku.newProcess` por reflexión, el cual ya no existe en versiones nuevas