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
