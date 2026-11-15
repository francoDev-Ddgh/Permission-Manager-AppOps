package com.francodev.appops.data

enum class WorkMode(
    val label: String,
    val description: String,
    val experimental: Boolean = false
) {
    SHIZUKU(
        label = "Shizuku",
        description = "Ejecuta los comandos a través del servicio Shizuku, sin necesidad de root. Shizuku debe estar en ejecución."
    ),
    ROOT(
        label = "Root",
        description = "Pensado para ejecutar comandos con root a través de KernelSU. Todavía no está implementado ni probado en ningún dispositivo.",
        experimental = true
    )
}
