# v0.3.0-test1: prueba de root y lecturas

Esta iteración implementa solicitud/verificación de root al crear la pantalla, en un
hilo de trabajo, y conecta CPU y zonas térmicas al shell autorizado. Batería y RAM
conservan sus fuentes Android. Si no se autoriza root, se intentan lecturas normales.
No instala root ni garantiza acceso a todos los sensores. Si el gestor ya recuerda
la autorización, puede no mostrar un diálogo nuevo. Si lo deniega o agota 30 s,
revisar el gestor de root y cerrar/abrir la app para repetir la solicitud.

Cada tarjeta muestra fuente o causa de ausencia. CPU requiere dos muestras válidas.
La temperatura es la zona válida más caliente con su nombre real, no necesariamente CPU.
Los comandos tienen timeout; la frecuencia de muestreo depende de su duración.
El estado ROOT indica la autorización inicial; un permiso revocado posteriormente
aparecerá como errores de lectura en tarjetas y log.

LOG exporta un archivo de texto mediante el selector Android. Los registros privados
persisten, se limitan a 2 MiB por sesión y se conservan las 10 sesiones más recientes.
Cada instancia de pantalla crea una sesión; LOG exporta la actual. Incluyen modelo,
versión, tiempos, estados, métricas y errores; revisar antes de compartir. No hay
envío automático ni recepción por correo implementados en esta iteración.

## Prueba física pendiente

1. Compilar con Signed test APK tras integrar. Descargar el artefacto firmado.
2. Instalar v0.3.0-test1. Si hay una versión debug con firma distinta, respaldar datos
   antes de desinstalarla; una instalación ya firmada con la clave estable se actualiza.
3. Abrir Dashboard, autorizar root y anotar el estado que muestra.
4. Mantener Dashboard visible con Minecraft en la otra pantalla durante 10 minutos.
5. Anotar exactamente qué tarjetas tienen datos, cuáles no y sus mensajes. Tomar foto.
6. Pulsar LOG y guardar el archivo, para adjuntarlo junto con la foto al diagnóstico.
7. Probar también root denegado y volver desde INFO/exportación: sin congelamientos,
   sin duplicación de muestreo y con causas visibles.

FPS sigue sin fuente implementada. Juego/pantalla seleccionable, actualizador,
correo, toast y publicación de pre-releases siguen pendientes. Esta es una iteración
de diagnóstico de v0.3, no el cierre de todas sus funciones ni una prueba física aprobada.
