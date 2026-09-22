# RGDS Dashboard v0.4.0-test1

Versión pública **de prueba**, firmada con la clave estable. Descarga
`RGDS-Dashboard.apk`. `SHA256SUMS.txt` permite comprobar el archivo y `update.json`
describe la versión para el actualizador. El código correspondiente está en este tag.

## Incluye

- Root al inicio, reintento y verificación periódica; CPU/sensores conectados al permiso.
- Elección de cualquier juego/emulador y pantalla; mover panel y solicitar abrir juego.
- FPS de superficie mediante SurfaceFlinger, experimental y elegido explícitamente.
- Sesiones persistentes, iniciar/finalizar prueba, incidencias y correo con adjunto revisable.
- Comprobación automática de Releases, descarga verificada e instalador Android.
- Logo/icono/banner originales, proyecto GPL-3.0-or-later y guías para contribuir/forks.
- Prototipo Linux separado en `linux/` (no es un port ROCKNIX validado).

## Límites y comprobaciones pendientes

El correo dedicado debe configurarse. No hay backend de envío desatendido ni buzón
conectado; abrir el compositor no demuestra entrega. La superficie FPS no acredita
automáticamente qué display usa ni los FPS internos del juego. ROMs sin datos válidos
muestran `--`. Las pruebas físicas en RG DS y otras consolas siguen pendientes.
La actualización automática busca y descarga tras aceptar; Android pide confirmación.
La primera instalación desde v0.2/v0.3 es manual. La ruta completa v0.4 → versión futura
requiere una prueba en consola. Compilar en verde no equivale a aprobar esas pruebas.

Si vienes de una APK debug con firma diferente, Android puede exigir desinstalarla:
exporta tus registros antes porque se borran sus datos. Las releases oficiales futuras
mantendrán la firma y aumentarán versionCode.
