# Correo de pruebas y reportes

## Implementado en v0.4

1. Crear/elegir un buzón dedicado (Gmail, Outlook o proveedor propio).
2. Guardar su dirección en Pruebas → Configurar correo de destino.
3. Iniciar prueba; al finalizar seleccionar correcto, fallo o interrumpida.
4. Revisar log e indicar pasos, esperado y observado.
5. Abrir correo: adjunto privado compartido con un permiso temporal de lectura.
6. Confirmar envío en el cliente. Verificar recepción y contenido del adjunto en el buzón.

También se pueden reportar incidencias sin finalizar la sesión o abrir sesiones
anteriores. Sin cliente configurado o sin red, exportar LOG y adjuntarlo desde PC.
El borrador y reintentos del correo dependen del cliente, no de Dashboard.
La app no conoce si el envío se completó: nunca muestra «entregado» por abrir un intent.

## Para que el asistente pueda leerlo

Conectar explícitamente el buzón mediante Gmail/Outlook en el entorno de trabajo, con
permisos de lectura, o adjuntar aquí el log. Recibir correo no dispara por sí solo
una sesión de depuración. Hace falta una tarea autorizada de lectura o abrir el reporte
en la conversación. No hay un buzón conectado ni una recepción comprobada por este cambio.

## Envío desatendido futuro

Si se desea envío sin tocar un cliente, se necesita un servicio HTTPS desplegado,
dominio/buzón y credenciales de envío solo en servidor. La app debe usar consentimiento
explícito, ID de sesión para deduplicar, límite de tamaño, cola privada y reintentos
acotados. El servidor requiere autenticación por usuario/dispositivo, límites de abuso,
política de retención y entrega SMTP/API. Una clave compartida dentro del APK no es una
credencial segura, incluso ofuscada. El código del servicio también será abierto.

Ese servicio no se ha desplegado; no se puede inventar un destinatario ni credenciales.
La versión pública usa el flujo revisable anterior, sin envío automático a terceros.
