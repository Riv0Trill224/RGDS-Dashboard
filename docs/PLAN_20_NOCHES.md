# RGDS Dashboard: plan de 20 noches

Fecha de preparación: 21 de septiembre de 2026. Base revisada: v0.2.0, commit `cdeb573`.

**Presupuesto:** una hora por noche, 20 sesiones. Las fechas son una ventana de trabajo asumida; no son una confirmación del vencimiento de Plus. Sesiones del 21 de septiembre al 10 de octubre de 2026. Una sesión perdida se reprograma y se reduce primero el alcance experimental, sin declarar pruebas pendientes como realizadas.

## Objetivo

Cerrar una versión Android utilizable y probada en RG DS, con registros que permitan reproducir errores entre versiones. Preparar la extensión a otras consolas Android de doble pantalla y un primer prototipo Linux/ROCKNIX. La compatibilidad universal y los FPS en todos los juegos no se consideran garantizados.

## Cambio de prioridad: prueba sin datos y canal público de test

El usuario reportó 10 minutos con Dashboard y Minecraft abiertos sin que apareciera información. La prueba no se considera superada. v0.2 solo solicita root dentro de INFO y no lo usa para el lector periódico del panel; FPS sigue sin implementación.

Se adelantan a la v0.3 estos requisitos obligatorios:
1. Solicitar/verificar root al arrancar y utilizarlo en las lecturas del panel, mostrando estado y errores.
2. Repetir la prueba de 10 minutos con trazas y comprobar por separado reloj, batería, RAM, CPU, sensores y FPS.
3. Preparar una firma estable y publicar APK en GitHub Releases como pre-release.
4. Incorporar comprobación automática de versiones test, descarga verificada e instalación confirmada por Android.

La ventana de trabajo es de **02:00 a 03:00, hora del centro de México**. Las tareas de logo, ampliación de consolas y Linux ceden tiempo si root, telemetría o actualizaciones requieren más sesiones. El calendario siguiente es orientativo; esta prioridad prevalece sobre el orden anterior. Linux sigue siendo un prototipo sujeto al tiempo restante.

## Estado de partida

- v0.2 contiene panel, fondo persistente, métricas disponibles e INFO con diagnóstico opcional mediante root.
- El repositorio documenta 18 pruebas locales aprobadas. No equivale a validación física ni acredita el último resultado de GitHub Actions.
- FPS: `--`. El juego activo no se identifica de forma fiable; INFO busca específicamente Minecraft.
- El diagnóstico actual se mantiene en memoria y se puede copiar. No existe envío por correo.
- No hay port Linux ni matriz de consolas verificadas.

## Calendario propuesto

| Noche | Fecha | Trabajo de la sesión | Evidencia para cerrar |
|---|---|---|---|
| 1 | 21 sep | Registrar fallo de 10 minutos y revisar ruta root de v0.2 | Requisito root al inicio y conexión al lector documentados |
| 2 | 22 sep | Implementar root al inicio, estado visible y lector privilegiado | Compilación y prueba de autorización/denegación; logs reales pendientes |
| 3 | 23 sep | Guardar una sesión de prueba con ID, versión y fechas | Log recuperable al cerrar la app y sin datos de otra sesión |
| 4 | 24 sep | Añadir inicio/fin de prueba, eventos y recuperación tras fallo | Sesión completa y sesión interrumpida correctamente identificadas |
| 5 | 25 sep | Preparar envío con adjunto y dirección dedicada | Correo recibido y adjunto legible; no basta abrir el compositor |
| 6 | 26 sep | Quitar la dependencia de Minecraft y elegir aplicación objetivo | Diagnóstico de dos paquetes distintos sin confundir el dashboard |
| 7 | 27 sep | Elegir la pantalla del juego y recordar la configuración | Funciona al intercambiar pantallas y detecta selección inválida |
| 8 | 28 sep | Toast, firma estable, canal test y actualizador; cerrar v0.3 | Pre-release y actualización test.1 → test.2 probadas; ampliar sesiones si falta |
| 9 | 29 sep | Evaluar fuente de FPS con los registros reales | Decisión documentada sobre Shizuku/root y ROM; sin estimaciones falsas |
| 10 | 30 sep | Implementar fuente viable o mejorar diagnóstico si no hay acceso | Lecturas justificadas o explicación concreta de la limitación |
| 11 | 1 oct | Botón para reportar incidencias con pasos y log | Incidencia reproducible, previsualizada y enviada correctamente |
| 12 | 2 oct | Preparar recepción y lectura de reportes | Cuenta conectada; reporte de prueba recuperado con adjunto |
| 13 | 3 oct | Adaptar disposición, controles y capacidades por consola | Matriz de compatibilidad y estados: probado/pendiente/no compatible |
| 14 | 4 oct | Logo original, icono de la app y banner de GitHub | Recursos legibles a tamaño real e integrados sin marcas de terceros |
| 15 | 5 oct | Regresión del actualizador, firma y reportes | Errores de red, APK incorrecto y recuperación comprobados |
| 16 | 6 oct | Regresión Android y documentación pública | Versión estable con límites y errores conocidos publicados |
| 17 | 7 oct | Elegir consola/versión ROCKNIX y revisar salidas gráficas | Entorno objetivo identificado y acceso a métricas comprobado |
| 18 | 8 oct | Prototipo Linux: batería, RAM, CPU, sensores disponibles y log | Muestras reales con unidades y ausencia de datos explícita |
| 19 | 9 oct | Panel Linux en una pantalla libre e integración de arranque | Prueba sin quitar foco al juego; si falta hardware, no se declara validado |
| 20 | 10 oct | Prueba final, empaquetado y lista de siguientes tareas | APK estable, estado del prototipo Linux y continuidad documentada |

Los días son una distribución de esfuerzo, no una promesa de duración técnica. Si el correo automático o los FPS consumen más tiempo, se conserva la exportación manual y se reduce el prototipo Linux. El envío automático completo depende además de un servicio de entrega y su prueba real.

## Rutina de una hora en PC con GitHub Desktop

1. **0–5 min:** Fetch origin/Pull, seleccionar la rama de prueba y revisar qué cambió.
2. **5–15 min:** descargar el APK de Actions, registrar versión/commit e instalar.
3. **15–35 min:** ejecutar dos o tres casos concretos, anotando esperado y observado.
4. **35–50 min:** cerrar y enviar el log; revisar el fallo y preparar una corrección acotada.
5. **50–60 min:** comprobar el cambio si alcanza el tiempo, guardar commit/estado y definir el siguiente paso. Una corrección sin probar queda marcada como pendiente.

Compilar y descargar puede consumir parte de la hora. Cada sesión debe terminar con un estado guardado aunque no termine la funcionalidad.

## Reportes y correo

- Se necesita una dirección dedicada elegida por el propietario; no se inventará una cuenta ni se incluirán contraseñas en el APK.
- Primer camino: guardar el log y abrir un correo con destinatario, asunto y adjunto. El usuario confirma el envío. Si la consola no tiene cliente de correo, exportar al PC.
- Objetivo posterior: al cerrar una prueba, envío automático mediante un servicio HTTPS que entregue al buzón dedicado. Debe manejar falta de red, reintentos acotados, duplicados, límites y confirmación de recepción. El servicio conserva las credenciales de correo.
- En la versión pública, el usuario revisa el reporte y decide enviarlo. El modo de pruebas automáticas es una opción explícita.
- Para leer correos aquí se conectará Gmail u Outlook con la cuenta correcta. Antes de automatizar la lectura debe recuperarse un mensaje y su adjunto en una prueba real. Hasta entonces, adjuntar el log en la conversación.
- Recibir un correo no implica que se haya corregido la app. Flujo: reporte → reproducción → cambio → build → instalación → comprobación con un nuevo reporte.
- Los registros se mantienen privados; no se copian al repositorio público sin revisión.

## Compatibilidad Android y juegos

- Seleccionar la pantalla que el usuario identifica como superior; nunca asumir que su ID es 0 o 1.
- Separar detección de aplicación, selección de pantalla, sensores y medición de FPS.
- Aceptar cualquier paquete objetivo; no mantener una lista cerrada de juegos.
- Mostrar solo lo que se pueda verificar. Una mención en dumpsys no prueba que el juego esté visible.
- En emuladores, inicialmente se identifica el emulador; el título de la ROM requiere integración específica.
- Probar primero RG DS/GammaOS. Las demás combinaciones de consola y ROM se marcan como pendientes hasta contar con pruebas.

## Port Linux/ROCKNIX

Es una implementación para Linux que compartirá el formato de reportes y el comportamiento del panel; el APK no funciona directamente en ROCKNIX. Hay que comprobar compositor, salidas disponibles, permisos, empaquetado y consumo en un equipo concreto. En dispositivos de una pantalla habrá que decidir si se usa una pantalla externa o un modo superpuesto, si el sistema lo permite.

El objetivo de las cuatro últimas sesiones es un prototipo acotado. FPS universales, todas las arquitecturas y todas las consolas quedan fuera de esa primera prueba.

Referencias oficiales consultadas el 21-09-2026:
- https://rocknix.org/devices/retroid/retroid-pocket-5/ — la segunda pantalla de este dispositivo requiere configuración manual.
- https://rocknix.org/configure/hdmi/ — comportamiento y configuración de salidas externas.
- https://developer.android.com/guide/components/intents-common — correo y adjuntos mediante intents.

## Texto corto para About en GitHub

> Un panel para la segunda pantalla de tu consola: consulta batería, memoria y sensores disponibles mientras juegas, personaliza el fondo y genera un diagnóstico cuando algo falla.

El README debe distinguir funciones actuales, en desarrollo y experimentales. Linux y FPS no se anuncian como disponibles antes de validarlos.

## Decisiones y bloqueos pendientes

- [ ] Configurar el flujo de publicación de Releases y su firma estable; la documentación se revisa mediante pull request.
- [ ] Clave estable de firma y configuración de secretos de compilación.
- [ ] Precisar qué campos faltaron en la prueba de 10 minutos y capturar logs.
- [ ] Dirección del correo dedicado y proveedor.
- [ ] Log INFO de v0.2 generado en la RG DS con juegos activos.
- [ ] Cliente de correo disponible en la consola o necesidad de enviar desde PC.
- [ ] Servicio de entrega para automatizar el correo, si se requiere sin intervención.
- [ ] Otra consola Android para ampliar la matriz y un equipo concreto para Linux.
- [ ] Validación del logo/banner antes de integrarlos como identidad definitiva.

## Seguimiento

Un resumen nocturno debe indicar sesión, noches pendientes dentro de esta ventana, objetivo y bloqueo. La cuenta atrás corresponde al plan supuesto, no a datos de facturación. El seguimiento no ejecuta desarrollo ni envía correos por sí solo.


## Compromiso open source

Todo el desarrollo propio será abierto: Android, Linux, scripts de compilación y el eventual servicio de reportes. Licencia preparada: GPL-3.0-or-later. Añadidos LICENSE, CONTRIBUTING.md y plantillas para reportes/pull requests. La comunidad podrá estudiar, compilar, modificar, distribuir y mantener forks. Los forks deberán poder cambiar sus destinos de actualizaciones/reportes y firmar con claves propias.

Antes de la primera pre-release: publicar la licencia y la guía, comprobar que la distribución acompaña su fuente e instrucciones y documentar qué componentes de terceros tienen otros avisos. La firma oficial y las credenciales quedan fuera del repositorio. La incorporación de estos archivos a la rama principal se revisa mediante pull request.
