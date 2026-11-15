# Permission-Manager-AppOps

App para Android que administra permisos, operaciones de AppOps y acceso a red por paquete, usando comandos del shell del sistema.

## Funciones

- **Permisos:** conceder y revocar permisos de runtime con `pm grant` y `pm revoke`.
- **AppOps:** consultar y modificar operaciones internas con `cmd appops`.
- **Red por paquete:** permitir o bloquear el acceso a red de una app con `cmd connectivity set-package-networking-enabled`.

## Compatibilidad

El proyecto está pensado para dispositivos **Motorola** y **Google Pixel**.

En otros dispositivos, las funciones podrían funcionar, pero no se garantiza nada.

## Requisitos

- ADB con acceso shell, o root.
- Versión mínima de Android: por definir.

## Advertencias

- Los comandos utilizados son internos de Android. No están documentados oficialmente y pueden cambiar entre versiones de Android o fabricantes.
- Revocar permisos o bloquear la red de una app puede hacer que deje de funcionar.
- Úsalo bajo tu propia responsabilidad.

## Licencia

Este proyecto está licenciado bajo GPL v3. Consulta el archivo LICENSE para más detalles.
