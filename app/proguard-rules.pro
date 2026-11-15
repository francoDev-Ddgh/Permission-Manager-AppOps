# Medium optimization: R8 shrinks and optimizes, but keeps names readable in stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Shizuku instancia el UserService por reflexión desde otro proceso, así que R8
# no puede verlo como usado y lo eliminaría. Hay que conservarlo junto con su Stub.
-keep class com.francodev.appops.shell.ShellUserService { *; }
-keep class com.francodev.appops.shell.IShellService { *; }
-keep class com.francodev.appops.shell.IShellService$Stub { *; }
-keep class com.francodev.appops.shell.ShellResult { *; }

# La API de Shizuku se usa por reflexión (newProcess) en versiones antiguas.
-keep class rikka.shizuku.** { *; }
