# Especificación v0.3 — root al inicio, pruebas y actualizaciones

**Estado: plan de implementación; estas funciones no están implementadas por este documento.**

## Prioridad 0: root al arrancar y lecturas reales

Reporte del usuario, recibido el 21-09-2026 (hora de México): mantuvo la consola con Dashboard y Minecraft abiertos durante 10 minutos y nunca apareció información. Falta precisar qué campos estuvieron ausentes; no se considera una prueba aprobada.

Inspección de v0.2, commit cdeb573: MainActivity crea StatsReader sin privilegios. StatsReader lee /proc/stat y /sys desde el UID de la app. La autorización root existe solo en DiagnosticActivity/DiagnosticCollector, no habilita las lecturas del panel. FpsProvider sigue siendo UnavailableFpsProvider. Root puede resolver algunas restricciones de lectura, pero no implementa FPS ni explica por sí solo la ausencia de reloj, batería o RAM.

Requisito obligatorio de v0.3: solicitar/verificar root automáticamente al arrancar, sin tener que entrar a INFO. Ejecutar la comprobación fuera del hilo de interfaz. Si Magisk ya recuerda la decisión, puede no mostrar otro diálogo. La app solicita la autorización; el usuario conserva el control de la concesión.

- Estado visible: SOLICITANDO, AUTORIZADO, DENEGADO, NO DISPONIBLE o ERROR/TIMEOUT.
- Tras autorizar, conectar un lector root real al muestreo del dashboard. No basta ejecutar su -c id.
- Mantener comandos fijos de solo lectura y límites de tiempo/salida. Evitar sondeos caros y solicitudes repetidas de su por cada métrica.
- Compartir el estado con INFO y registrar la fuente/permisos de cada dato. Detectar pérdida de autorización y descartar muestras antiguas.
- Si se deniega o no existe su, explicar el motivo y ofrecer reintento explícito; conservar las métricas públicas que funcionen.
- Mostrar última actualización y causa de ausencia por métrica. Revisar ciclo de vida y actualización del panel si tampoco aparecen batería/RAM/reloj.
- FPS sigue siendo un requisito independiente, sujeto a una fuente verificada del juego en la pantalla elegida.

## 1. Registro de pruebas

Controles: INICIAR PRUEBA, FINALIZAR PRUEBA, VER LOG y ENVIAR REPORTE. Guardar inmediatamente un encabezado de sesión, añadir eventos acotados fuera del hilo de interfaz y cerrar con resultado. Conservar un reporte incompleto si el proceso termina; un cierre abrupto o ANR no siempre permite escribir un evento final.

Cada reporte debe contener:
- Versión de esquema, ID aleatorio de sesión y appVersion/versionCode; commit o build ID cuando esté disponible.
- Inicio y fin en ISO 8601 con zona horaria, duración monotónica y resultado: correcto/fallo/interrumpido.
- Fabricante/modelo, Android/ROM, modo de permisos y capacidades disponibles.
- Pantalla del dashboard, pantalla objetivo, aplicación objetivo y origen de la identificación (selección manual/evidencia del sistema).
- Métricas con valor, unidad, fuente y motivo cuando no están disponibles.
- Errores de la app, permisos denegados, timeouts y límites de captura.
- Pasos realizados, comportamiento esperado, comportamiento observado y notas del probador.

Texto UTF-8 legible para humanos; formato estructurado versionado para análisis posterior. Nombre propuesto: `rgds-v<version>-<fechaUTC>-<sessionId>.log`. Nunca confundir sesiones de versiones distintas. Establecer límites de tamaño/retención; avisar cuando una captura queda recortada. No iniciar capturas ilimitadas de logcat del sistema.

No añadir credenciales, tokens, números de serie, rutas personales o contenido de otras apps al reporte mínimo. El diagnóstico RAW puede contener paquetes y nombres de ventanas: ofrecerlo aparte, revisable, y no enviarlo automáticamente como parte del reporte mínimo.

## 2. Entrega por correo

En la primera implementación, dirección configurable, asunto `[RGDS][v<version>][<modelo>][<sessionId>] <resultado>` y archivo adjunto. Mostrar una vista previa y abrir el cliente de correo. Conservar copia local y ofrecer exportación si no hay cliente.

No marcar el reporte como enviado al abrir el compositor: Android no confirma que el proveedor lo haya entregado. Para la prueba de aceptación se verificará la recepción en el buzón dedicado y se abrirá el adjunto.

El envío desatendido es una segunda implementación con servicio HTTPS, credenciales solo en servidor, aceptación del servicio, ID para evitar duplicados y reintentos cuando vuelva la red. El servicio y la cuenta aún están por definir. No incluir contraseñas SMTP ni una clave privada de envío compartida en el APK.

## 3. Cualquier aplicación objetivo

Sustituir las expresiones y etiquetas fijas de Minecraft por una selección de paquete y un analizador genérico. Validar la entrada y no interpolarla sin protección en comandos shell. Preferir interpretar localmente resultados de comandos fijos.

Seleccionar aplicación objetivo por medios disponibles y permitir entrada manual cuando Android restrinja su enumeración. El sistema debe mostrar claramente cuándo la elección es manual y cuándo existen pruebas de que la aplicación ocupa la pantalla elegida. No tratar la selección manual como detección automática.

No deducir visibilidad de una coincidencia de texto ni asociar PID/display de líneas ajenas. Excluir las superficies del propio dashboard. En emuladores, identificar el paquete del emulador sin inventar el nombre del juego.

## 4. Pantalla superior configurable

Enumerar displays disponibles y permitir que el usuario marque cuál corresponde físicamente a la pantalla superior. Mantener el ID interno separado de la etiqueta física. Revalidar al recrear la actividad o cambiar displays; si una selección dejó de existir, pedir otra en lugar de medir otra pantalla.

La v0.3 prepara diagnóstico y selección. FPS queda condicionado a los datos de INFO y a la viabilidad de un proveedor autorizado. Si no hay evidencia, mostrar `--` y la causa. No mostrar Hz ni FPS de la interfaz como FPS del juego.

## 5. Identidad y repositorio

Toast al abrir desde un inicio nuevo: **RGDS Dashboard · GitHub**. Una URL larga se corta fácilmente; en ACERCA se mostrará completa y pulsable: https://github.com/Riv0Trill224/RGDS-Dashboard . Evitar repetir el aviso al volver del selector de archivos o de INFO.

Logo y banner: identidad original basada en dos pantallas y monitorización, legible como icono pequeño y en modo oscuro. Mantener el nombre RGDS Dashboard durante esta etapa. Revisar el resultado antes de incorporar los recursos definitivos.

## 6. Publicación de pruebas y actualizaciones desde GitHub

Usar Releases públicas marcadas como **pre-release**, empezando por `v0.3.0-test.1`, con APK instalable, notas, errores conocidos y estado de pruebas. Cada APK posterior tendrá versionCode estrictamente mayor, aunque solo cambie el sufijo test.N.

Antes de distribuir: crear una clave de firma estable, respaldarla y guardarla como secreto de compilación; nunca publicarla ni regenerarla por ejecución. La firma debug de v0.2 puede no coincidir: documentar que el cambio inicial puede requerir reinstalar y perder preferencias. Después, probar dos APK test consecutivos con la misma firma y verificar conservación de ajustes.

Flujo previsto:
1. Compilar, ejecutar pruebas pertinentes y lint; esos controles deben bloquear la publicación si fallan. El workflow actual permite subir artifacts aunque fallen: corregir esto para Releases.
2. Probar el APK en RG DS y publicar una pre-release con los fallos conocidos claramente descritos. Mantener el tag asociado al commit probado.
3. La app consulta automáticamente el canal de pruebas al abrir, con intervalo mínimo para evitar consultas repetidas, y ofrece una comprobación manual.
4. Consultar la lista de Releases y filtrar pre-releases publicadas; no depender de /releases/latest, que no representa necesariamente el canal test. Validar versión y APK esperado del repositorio oficial.
5. Avisar de nueva versión y descargar por HTTPS. Verificar hash cuando esté disponible, paquete, versionCode y firma compatibles. Descartar descargas incompletas, versiones anteriores y APK de otra firma.
6. Abrir el instalador de Android y guiar el permiso de instalar apps desde esta fuente cuando sea necesario. La comprobación/descarga puede ser automática; la instalación inicial de este diseño conserva la confirmación del sistema y no usa su para instalar silenciosamente.
7. Sin red o ante error de GitHub, conservar la versión instalada y un mensaje útil; no bloquear el inicio del dashboard.

La publicación real depende de acceso de escritura a GitHub y de la firma estable. En esta revisión solo se define el alcance; no existe una Release publicada ni un actualizador implementado por estos documentos.

Referencias oficiales:
- https://docs.github.com/en/rest/releases/releases
- https://developer.android.com/studio/publish/app-signing
- https://developer.android.com/studio/publish

## Aceptación v0.3

- [ ] Inicio limpio solicita root sin abrir INFO y no congela la interfaz.
- [ ] Concesión, denegación, su ausente, timeout y revocación tienen estados correctos.
- [ ] Autorizar root cambia realmente la ruta de lectura de CPU/sensores; cada dato ausente tiene explicación.
- [ ] Prueba de 10 minutos con juego abierto: registrar qué métricas actualizan y cuáles no, sin llamar FPS a los Hz.
- [ ] Dos APK test consecutivos actualizan con la misma firma y conservan preferencias.
- [ ] El actualizador detecta la pre-release correcta, maneja falta de red y rechaza paquete/firma/versión incorrectos.
- [ ] Release descargable con APK, notas, commit y pruebas documentadas; no confundir artifact de Actions con Release.
- [ ] Dos pruebas consecutivas producen registros separados con la versión correcta.
- [ ] Sesión interrumpida recuperable; UI utilizable mientras se guarda el log.
- [ ] Reporte exportable sin cliente de correo y con/sin red.
- [ ] Correo recibido con adjunto correcto; no hay falso estado de entrega.
- [ ] Diagnóstico de dos juegos distintos y de un emulador sin filtro Minecraft.
- [ ] Juego arriba/panel abajo; tras intercambiar, selección coherente y sin medir el panel.
- [ ] Sin permisos, con permisos denegados y con permiso revocado no hay cierres.
- [ ] Toast una vez por inicio nuevo y enlace accesible en ACERCA.
- [ ] Fondos, controles, batería y RAM de v0.2 conservan funcionamiento.
- [ ] Build, tests pertinentes y lint revisados; APK instalado y probado en RG DS.

La captura de errores de la app ayuda a depurar el dashboard; no otorga acceso completo a los errores internos de otros juegos.


## Requisito transversal: forks y aportes comunitarios

Código propio abierto bajo GPL-3.0-or-later, con LICENSE y CONTRIBUTING. Mantener los avisos de terceros. Actualizador: URL/propietario/repositorio/canal configurables al compilar; validar descargas contra el destino configurado y la firma de esa distribución. Reportes: destinatario/servidor configurables sin secretos embebidos. Un fork puede construir y probar sin credenciales oficiales. Las releases incluyen acceso al código correspondiente y pasos de compilación. Si se desarrolla un servidor propio de correo, también se publica su implementación; sus credenciales permanecen privadas.
