# Compatibilidad y aceptación v0.4

| Entorno | Estado real |
|---|---|
| Android 8.0+ | APIs mínimas previstas; build/lint/unitarias en CI |
| RG DS / GammaOS Lite | Objetivo principal; v0.2 reportada sin datos, prueba física v0.4 pendiente |
| Otras consolas Android de doble pantalla | Selección genérica implementada; cada modelo/ROM pendiente |
| Sin root / root denegado | Lecturas públicas y estados de ausencia; validar físicamente |
| FPS SurfaceFlinger | Experimental; requiere superficie seleccionada y tiempos recientes válidos |
| Linux en terminal | Prototipo Python, pruebas sintéticas y ejecución de humo |
| Linux con Tk | Ventana configurable; necesita entorno gráfico y Tk |
| ROCKNIX | Sin equipo/imagen concretos para validar o empaquetar |

## Prueba física antes de anunciar estabilidad

- Instalar APK firmada. Autorizar, denegar, revocar y reintentar root sin bloquear UI.
- Repetir diez minutos con Minecraft y con otra app; registrar cada tarjeta y causa.
- Cambiar pantalla objetivo y mover panel; desconectar destino y comprobar estado inválido.
- FPS: elegir superficie, cerrar juego y verificar que deja de mostrar valor obsoleto.
- Iniciar/finalizar dos pruebas y comprobar logs separados con versión y resultado.
- Cerrar proceso durante prueba: recuperar archivo sin END como sesión interrumpida.
- Enviar reporte al buzón dedicado, abrir adjunto allí y confirmar contenido.
- Publicar siguiente test con versionCode mayor y misma firma; instalar desde v0.4,
  comprobar permiso de fuente desconocida, cancelación y conservación de ajustes.
- Sin red, hash incorrecto, firma/paquete/versión incorrectos: rechazar sin sustituir app.
- Probar gamepad, pantallas pequeñas, fondo, regreso desde INFO/correo/instalador.

Las pruebas unitarias de URLs, firma, tiempos FPS y logs no reemplazan estos casos.
