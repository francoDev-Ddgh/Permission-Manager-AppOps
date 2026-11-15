package com.francodev.appops.data

enum class WorkMode(val label: String, val description: String) {
    SHIZUKU(
        label = "Shizuku",
        description = "Ejecuta los comandos a través del servicio Shizuku, sin necesidad de root. Shizuku debe estar en ejecución."
    ),
    ROOT(
        label = "Root",
        description = "Ejecuta los comandos con acceso root a través de KernelSU. KernelSU debe estar instalado y autorizar esta app."
    )
}
