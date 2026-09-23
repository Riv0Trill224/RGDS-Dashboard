# Relay privado de desarrollo (pendiente de desplegar)

Python 3.8+, biblioteca estándar, GPL-3.0-or-later. Recibe un reporte autenticado
y lo adjunta a un correo cuyo único destinatario es `riv0trill224@icloud.com`.
No toma destinatarios del cliente y no incluye contraseñas en el APK/repositorio.

Se necesita un host con HTTPS y una cuenta remitente que admita SMTP con TLS implícito.
El buzón de destino iCloud no obliga a usar iCloud como remitente. Configurar mediante
secretos privados del proveedor de hosting: `RELAY_TOKEN` (aleatorio, mínimo 32 caracteres),
`SMTP_HOST`, `SMTP_PORT` (465 por defecto), `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM`.
`RECEIPTS_DB` debe apuntar a almacenamiento persistente y privado.

Ejecutar `python3 server.py`. Escucha solo en 127.0.0.1:8080; poner detrás de un proxy
HTTPS autenticado por el token, sin registrar Authorization ni cuerpos, y limitar
las solicitudes a 6 MiB. Es un servicio pequeño para pruebas privadas, no multitenant.
No exponer el puerto HTTP directamente. Generar el token localmente, no en una conversación.

En Dashboard: Pruebas → Autoenvío de desarrollo. Introducir URL HTTPS `/reports`
y token del dispositivo; activar la captura. Envío al inicio, cada cinco minutos
mientras el proceso siga vivo y al salir de la pantalla (se limita la captura a una
por minuto). Cola persistente de hasta diez reportes; reintentos al abrir o cada cinco
minutos. Si Android mata el proceso no hay garantía de envío final; no se instala
un servicio permanente ni se impide que Android gestione batería.

Desactivado por defecto en APK pública. Sin servidor/token solo guarda pendientes.
El token es privado de esta instalación; se revoca/cambia en servidor si se expone.
No dar el mismo token a usuarios públicos. Para ampliar a comunidad se necesita
registro de dispositivos y tokens individuales.

Una respuesta smtp_accepted significa aceptación por el servidor de correo, no entrega
en bandeja. Verificar primero el adjunto en iCloud. SQLite evita duplicar IDs ya
confirmados; una caída entre aceptación SMTP y persistir el resultado puede duplicar
un envío. Mantener backups/permisos del DB y rotarlo al terminar desarrollo.

Pruebas: `python3 -m unittest discover -s report-relay`. Usan un remitente simulado:
**no envían correos**. Este repositorio no despliega por sí solo el servicio ni demuestra
recepción de reportes.
