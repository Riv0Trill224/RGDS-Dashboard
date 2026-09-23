# Integración del Worker rgds-logger (modo económico)

URL de la app: `https://rgds-logger.riv0trill224.workers.dev/log`.
El Worker inicial devolvía ok/message/receivedAt; eso no acredita SMTP y no incluía
el ID de reporte. **Hay que reemplazar su código por worker.mjs y desplegarlo.**

1. Cloudflare → Workers & Pages → rgds-logger → Edit code. Pegar worker.mjs y Deploy.
2. Settings → Variables and Secrets: secreto `RGDS_API_KEY`, mínimo 32 caracteres
   aleatorios. Mantener su valor privado; escribirlo solo en Cloudflare y en la app.
3. Mantener Observability / Persist logs to Workers dashboard habilitado, sampling 100%.
4. En v0.4.0-test3: Pruebas → Autoenvío de desarrollo. Activar, verificar la URL anterior
   e introducir el valor del secreto en el campo token. Guardar prepara un primer envío.
5. Buscar `RGDS_DIAGNOSTIC` en Observability → Logs y comprobar reportId/contenido.

No cambia planes ni configura recursos de pago. No usa KV/R2/D1 ni servicio de correo.
Consulta los límites vigentes de tu plan: Workers Logs Free documenta retención de
3 días; no es un archivo permanente. La app manda los últimos 16000 caracteres del log
y marca si hay recorte, muy por debajo del límite de logs de 256 KiB por invocación.
El log completo continúa en almacenamiento privado de la consola (retención local existente).
Las colas confirmadas se retiran; no se afirma almacenamiento permanente ni correo entregado.

No hay deduplicación persistente: un reintento puede repetir reportId. Tampoco limita
por dispositivo: este token es solo para tu prueba privada, no compartir con la comunidad.
No publiques tokens en GitHub ni capturas. La app no incluye ninguna clave predefinida.
Sin Worker actualizado/token válido deja los reportes pendientes y muestra el error.

Pruebas locales: `node --test cloudflare/worker.test.mjs`. Son simuladas, no llaman
al Worker desplegado ni envían datos del dispositivo. El despliegue y un reporte real
desde la consola todavía deben verificarse.

Referencia: https://developers.cloudflare.com/workers/observability/logs/workers-logs/
