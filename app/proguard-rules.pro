# R8 en modo agresivo. Las reglas de abajo existen solo para lo que R8 no puede
# deducir por sí solo: código que se carga por reflexión desde otro proceso.

# Números de línea para poder leer los fallos que reporten los usuarios.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Permite a R8 cambiar la visibilidad de clases y miembros; habilita más
# optimizaciones (inline, eliminación de accesos, etc.).
-allowaccessmodification

# Fuera los logs de depuración del APK final.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}

# Shizuku arranca el UserService en otro proceso por reflexión: si R8 renombra
# o elimina estas clases, el servicio deja de funcionar.
-keep class com.francodev.appops.shell.ShellUserService { *; }
-keep class com.francodev.appops.shell.IShellService { *; }
-keep class com.francodev.appops.shell.IShellService$* { *; }
-keep class com.francodev.appops.shell.ShellResult { *; }

# La API de Shizuku se usa por reflexión para el camino antiguo
# (newProcess en Shizuku < 13.1.5), así que no se puede ofuscar.
-keep class rikka.shizuku.** { *; }

# Cualquier Parcelable expone CREATOR por reflexión.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
