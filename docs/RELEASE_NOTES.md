# RGDS Dashboard v0.4.0-test6 — diagnóstico FPS sin PC

VersionCode 9. Pruebas → Diagnóstico FPS sin PC permite ejecutar una captura
manual de SurfaceFlinger con root, sin terminal ni conexión USB. También se abre
con Diagnosticar en el aviso del selector vacío.

Compara la lista por PATH, la lista usando /system/bin/dumpsys y el informe completo.
Conserva stdout, stderr, salida vacía, código de salida, timeout y truncamiento.
Cada consulta está limitada por el ejecutor existente. El resumen se puede copiar
y el informe completo compartir como adjunto con una app instalada. El último
informe queda en almacenamiento privado para recuperarlo al volver a abrir.

El RAW puede contener nombres de otras apps/ventanas. No se envía automáticamente
al correo ni a Cloudflare. El usuario elige compartirlo. No requiere configuración
del Worker. Esta captura desde la app no sustituye la comparación independiente
con ADB, pero aporta el informe completo que faltaba. No cambia la fuente de FPS
ni afirma que la ROM sea incompatible. Prueba física aún pendiente.

# RGDS Dashboard v0.4.0-test5 — selección y diagnóstico de superficies

VersionCode 8. El aviso «No hay superficies compatibles» ocultaba resultados
muy distintos. Ahora distingue error del comando, lista truncada, permisos y lista
sin candidatos. Guarda SURFACE_LIST (salida, stderr y estado) en el log incluso
si no se pudo elegir nada.

El selector conserva superficies sin el nombre del paquete, ordena primero las
coincidentes y marca las demás como sin verificar. No asigna automáticamente
superficies a Minecraft ni deduce su pantalla. Admite nombres Unicode y símbolos
como $ dentro de un único argumento entre comillas; rechaza comillas simples y
controles. Pruebas de shell verifican que los símbolos se pasan literalmente.

La captura test4 demuestra ausencia de candidatos, pero no cuál de los filtros
falló. No hay FPS aprobados en hardware. Actualizar, elegir superficie, jugar 40 s
y compartir LOG si no aparecen FPS. Se conserva Cloudflare; despliegue/recepción
real no verificados en esta entrega.

# RGDS Dashboard v0.4.0-test4 — diagnóstico compatible con Android

VersionCode 7. Corrige la llave literal sin escapar en la expresión del proceso
objetivo: causaba PatternSyntaxException en INFO en Android/ICU (test2 y test3).
Añade comprobación nativa ICU en CI, además de pruebas JVM para delimitadores y
paquetes parecidos. La prueba reproduce el rechazo del patrón anterior.

Este arreglo no demuestra que FPS funcione: tras actualizar, repetir selección de
superficie, mantener el juego activo 40 segundos y revisar LOG e INFO/RAW.
Se mantiene la firma y el canal de actualización. Sin prueba física aprobada aún.

# RGDS Dashboard v0.4.0-test3 — integración Cloudflare

VersionCode 6. Añade rgds-logger como destino HTTPS preconfigurado, extractos acotados
para Workers Logs y acuse autenticado por reportId. Cloudflare no equivale a SMTP:
la interfaz dice recibido por Worker, no correo entregado. No configura pagos ni recursos
KV/R2/D1. Conserva el log completo en la consola.

**Acción necesaria:** actualizar el Worker desplegado con cloudflare/worker.mjs,
configurar RGDS_API_KEY e introducir su valor privado en Pruebas → Autoenvío de desarrollo.
El receptor inicial ok/message/receivedAt no basta; los reportes quedan pendientes
hasta recibir el nuevo acuse. Ver cloudflare/README.md. No se ha desplegado este cambio
desde GitHub ni verificado recepción real desde la consola.

## Cambios conservados del hotfix test2

APK pública de prueba firmada con la misma clave. Se actualiza sobre
v0.4.0-test1 sin desinstalar. Descarga RGDS-Dashboard.apk o usa Opciones → Buscar actualización.

- Aviso visible del resultado de root al iniciar o reintentar.
- Causas FPS más precisas: salida vacía, sin historial, ceros, frames pendientes o tiempos antiguos.
- Respuesta acotada del comando FPS en el log cada 30 segundos, incluyendo superficie y pantalla elegidas.
- Correo manual preconfigurado: riv0trill224@icloud.com.
- Autoenvío de desarrollo opt-in: cola privada persistente, captura cada cinco minutos,
  al inicio y al salir de pantalla, con reintentos HTTPS y confirmación SMTP del servidor.
- Código abierto del relay SMTP en report-relay/, autenticación y deduplicación de IDs.

**Envío desatendido no activo:** requiere desplegar el relay con HTTPS, configurar
credenciales remitentes solo en servidor y URL/token privado en la app. Sin servidor
guarda pendientes, no manda correos. No hay recepción de reporte verificada.
Android puede cerrar el proceso sin permitir el último envío; el modo queda desactivado
por defecto en la distribución pública. Al cerrar prueba todavía se puede enviar desde
el cliente de correo de la consola, sin retirar la SD.

**FPS en RG DS sigue sin validación.** Este hotfix permite obtener la evidencia que
faltaba; no garantiza una lectura en ROMs sin historial de SurfaceFlinger.
Seleccionar superficie, dejar el juego activo al menos 40 segundos y enviar LOG.

Se conservan selección juego/pantalla, panel, fondos, actualizador, GPL y prototipo Linux.
ROCKNIX sigue sin validación física. La prueba de actualización e instalación requiere
confirmación en la consola. Compilar en verde no reemplaza esas pruebas.
