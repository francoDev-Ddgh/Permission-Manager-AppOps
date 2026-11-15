package com.francodev.appops.data

data class ManagedPermission(
    val name: String,
    val title: String,
    val description: String
)

val managedPermissions: List<ManagedPermission> = listOf(
    ManagedPermission(
        name = "WRITE_SECURE_SETTINGS",
        title = "Modificar Ajustes Seguros",
        description = "Permite cambiar ajustes protegidos del sistema. La app lo usa para activar o desactivar los servicios de accesibilidad y el acceso a notificaciones. Android no lo concede desde la pantalla normal, así que hay que darlo por ADB o con root."
    ),
    ManagedPermission(
        name = "CAMERA",
        title = "Cámara",
        description = "Permite a una app usar la cámara. Desde esta app puedes concederlo o revocarlo para cada aplicación instalada. Al cambiarlo, el efecto es inmediato."
    ),
    ManagedPermission(
        name = "BLUETOOTH_CONNECT",
        title = "Conexión Bluetooth",
        description = "Permite conectarse a dispositivos Bluetooth ya emparejados, como audífonos o relojes. Desde esta app puedes concederlo o revocarlo para cada aplicación."
    ),
    ManagedPermission(
        name = "BLUETOOTH_SCAN",
        title = "Buscar dispositivos Bluetooth",
        description = "Permite buscar dispositivos Bluetooth cercanos. Algunas apps lo necesitan para encontrar accesorios nuevos. Desde esta app puedes controlarlo por aplicación."
    ),
    ManagedPermission(
        name = "CHANGE_WIFI_STATE",
        title = "Cambiar estado del Wi-Fi",
        description = "Permite a una app encender o apagar el Wi-Fi. Desde Android 10 esta capacidad está muy restringida, así que el resultado depende de la versión del sistema y del fabricante."
    ),
    ManagedPermission(
        name = "READ_MEDIA_IMAGES",
        title = "Imágenes",
        description = "Permite leer las fotos y capturas guardadas en el almacenamiento. Sirve para que galerías y editores accedan a tus imágenes. Desde esta app puedes permitirlo o bloquearlo por aplicación."
    ),
    ManagedPermission(
        name = "READ_MEDIA_AUDIO",
        title = "Audio",
        description = "Permite leer archivos de música, podcasts y audios guardados. Algunas apps lo necesitan para reproducir o buscar tus canciones. Puedes controlarlo para cada aplicación desde aquí."
    ),
    ManagedPermission(
        name = "READ_MEDIA_VIDEO",
        title = "Video",
        description = "Permite leer los videos guardados en el almacenamiento. Lo usan las apps de galería y reproducción para mostrar tus archivos. Desde esta app puedes concederlo o revocarlo."
    ),
    ManagedPermission(
        name = "MANAGE_EXTERNAL_STORAGE",
        title = "Acceso a todos los archivos",
        description = "Permite leer y modificar cualquier archivo del almacenamiento, no solo fotos, audio o video. Es un permiso especial y muy sensible. Solo conviene darlo a administradores de archivos de confianza."
    ),
    ManagedPermission(
        name = "VIBRATE",
        title = "Vibrar",
        description = "Permite que la app haga vibrar el teléfono. Se usa para avisos, alarmas y respuestas táctiles. Desde esta app puedes permitirlo o bloquearlo para cada aplicación."
    ),
    ManagedPermission(
        name = "PICTURE_IN_PICTURE",
        title = "Imagen en imagen",
        description = "Permite mostrar una ventana flotante mientras usas otra app, por ejemplo para ver un video. Es un permiso especial del sistema y no aparece en la lista normal de permisos."
    ),
    ManagedPermission(
        name = "SYSTEM_ALERT_WINDOW",
        title = "Mostrar sobre otras apps",
        description = "Permite dibujar ventanas o burbujas encima de otras aplicaciones. Muchas apps lo usan para chats flotantes o herramientas rápidas. Puede usarse para tapar contenido, así que conviene revisarlo."
    ),
    ManagedPermission(
        name = "BIND_ACCESSIBILITY_SERVICE",
        title = "Servicio de accesibilidad",
        description = "Permite que una app lea el contenido de la pantalla y actúe en nombre del usuario. Es muy potente, por eso Android solo lo activa desde Ajustes. Desde esta app puedes habilitarlo o deshabilitarlo."
    ),
    ManagedPermission(
        name = "BIND_NOTIFICATION_LISTENER_SERVICE",
        title = "Acceso a notificaciones",
        description = "Permite leer las notificaciones que llegan al teléfono y, en algunos casos, descartarlas. Lo usan apps de organización y accesibilidad. Puedes activarlo o desactivarlo desde esta app."
    ),
    ManagedPermission(
        name = "POST_NOTIFICATIONS",
        title = "Mostrar notificaciones",
        description = "Permite que la app muestre notificaciones en la barra de estado. Sin este permiso la app puede funcionar, pero no te avisará de nada. Desde Android 13 es un permiso de ejecución."
    ),
    ManagedPermission(
        name = "INTERNET",
        title = "Internet",
        description = "Permite a la app usar la red. Desde esta app puedes permitir o bloquear el acceso a internet de cada aplicación por separado, sin desinstalarla."
    )
)
