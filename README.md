# RGDS Dashboard · 0.2.0

**Tu juego en una pantalla. La información de tu consola en la otra.**

RGDS Dashboard convierte una pantalla de tu consola en un panel con reloj, batería, memoria y los sensores que el sistema permita consultar. Puedes elegir tu propio fondo y ajustar su oscuridad para leer la información mientras juegas en la otra pantalla.

La aplicación consulta los datos disponibles en Android y los actualiza mientras el panel está visible. Cuando un dato no se puede leer, lo indica. El botón **INFO** reúne información del dispositivo y sus pantallas para ayudarnos a investigar problemas.

### Estado actual

La **v0.2.0** está diseñada inicialmente para Anbernic RG DS con GammaOS Lite 1.2.2. La compilación y las pruebas locales están documentadas; la validación en la consola sigue pendiente en [la lista de pruebas](docs/TESTING.md). **Los FPS todavía no están implementados** y se muestran como `--`. La búsqueda específica de Minecraft existe solamente en el diagnóstico actual.

### Próximos pasos

La v0.3 priorizará solicitar root al iniciar y utilizarlo en las lecturas del panel, guardar registros de prueba y diagnosticar el juego seleccionado, con selección de pantalla. También prepararemos versiones públicas de prueba en GitHub Releases y su actualización desde la app, con firma estable. Los reportes por correo forman parte de esta etapa. Después ampliaremos las pruebas a otras consolas Android de doble pantalla y prepararemos un prototipo para Linux/ROCKNIX. Estas funciones están **planificadas**, no incluidas en v0.2.

- [Plan de 20 noches](docs/PLAN_20_NOCHES.md)
- [Alcance y criterios de la v0.3](docs/V0_3_SPEC.md)

## Información técnica

Ventana horizontal de referencia: **640×480 píxeles**, en cualquiera de sus dos pantallas. Paquete `com.rgds.dashboard`, Android mínimo 8.0 (API 26). El panel básico no necesita root, Shizuku ni Android Studio; INFO ofrece diagnóstico root opcional.

## Qué incluye

- Hora y fecha según la configuración del dispositivo.
- Batería mediante `BatteryManager`; RAM utilizada/total mediante `ActivityManager.MemoryInfo`.
- CPU global mediante diferencias de contadores de `/proc/stat`, si el sistema permite leerlo. La primera muestra y las lecturas inválidas muestran «No disponible».
- Temperatura de la zona térmica accesible más caliente, identificada por su `type`; no se presume que sea CPU. Se interpreta `temp` en miligrados Celsius, según la interfaz Linux thermal.
- Temperatura de batería mediante el broadcast persistente `ACTION_BATTERY_CHANGED`.
- Wallpaper `centerCrop`, selector de documentos Android, permiso URI persistente y oscurecimiento de 0–90%, guardado en preferencias privadas.
- Interfaz oscura original, botones enfocables con cruceta, landscape, modo inmersivo y pantalla encendida mientras la actividad está visible.
- **INFO / DIAGNÓSTICO**: dispositivo, displays, CPU, frecuencias, thermal, devfreq, SurfaceFlinger y relación informativa con Minecraft; resumen, RAW seleccionable, actualizar y copiar. Root opcional de solo lectura mediante Magisk.

CPU, sensores y servicios son best-effort. Cada fuente falla de forma independiente y se vuelve a intentar; no se conserva una lectura antigua como si fuera actual. Un único worker toma muestras aproximadamente cada segundo, sin bloquear la interfaz. Los fondos se decodifican en otro worker, con reducción de resolución para limitar memoria.

## INFO / diagnóstico v0.2

1. Abre Minecraft en una pantalla y RGDS Dashboard en la otra.
2. Pulsa **INFO**. La primera instantánea usa las APIs públicas y comandos sin root.
3. Pulsa **AUTORIZAR ROOT** para ejecutar `su -c id` y responder al diálogo de Magisk, si aparece. Si no hay root o se rechaza, INFO sigue recopilando lo accesible sin privilegios.
4. Pulsa **ACTUALIZAR** cuando cambie el juego o su pantalla. Solo se ejecuta una recopilación a la vez; durante ella aparece «Recopilando diagnóstico…».
5. Usa **VER DIAGNÓSTICO RAW** y **COPIAR DIAGNÓSTICO**. El resumen puede acortarse; RAW conserva salidas, errores y códigos por comando. RAW muy grande se visualiza por páginas y se copia por partes numeradas si excede el tamaño seguro del portapapeles.
6. Regresa con **VOLVER** o Back. El dashboard conserva wallpaper, estadísticas y `-- FPS`.

INFO distingue el display actual de su ventana del ID capturado al recopilar. La detección de Minecraft indica menciones en las salidas, no garantiza que sea la actividad visible. Solo muestra PID/Display con evidencia explícita y deja `--` ante formatos desconocidos. [Comandos, límites y pruebas de diagnóstico](docs/DIAGNOSTICS.md).

## FPS y juego en la otra pantalla

**Esta versión muestra `-- FPS`.** No genera FPS simulados ni mide frames del dashboard. `FpsProvider` separa la presentación del muestreo; `UnavailableFpsProvider` es la implementación inicial.

El juego activo se muestra como **No disponible**: las API públicas sin permisos adicionales no permiten identificar de forma fiable qué juego está ejecutándose en la otra pantalla. No se pide acceso a estadísticas de uso ni accesibilidad para ofrecer una estimación engañosa.

La etapa Shizuku debe incorporar un `ShizukuFpsProvider` con autorización explícita, selección del display/juego/capa de SurfaceFlinger, diferencias entre timestamps de presentación y manejo de desconexión, permisos revocados y muestras obsoletas. Nunca debe seleccionar la capa de RGDS Dashboard. Ver [arquitectura](docs/ARCHITECTURE.md).

## Dos pantallas

Abre el juego y mueve/abre RGDS Dashboard en la otra pantalla mediante las funciones de GammaOS. Puedes intercambiar su posición: no existe ningún ID de pantalla ni posición superior/inferior fijado en el código. Android/GammaOS controla dónde lanza las actividades; esta app no fuerza ni mueve el juego.

El dashboard usa el tamaño real de su ventana. El muestreo empieza en `onStart` y termina en `onStop`, por lo que una actividad visible pero pausada sigue actualizándose. La petición landscape y mantener pantalla encendida pueden quedar condicionados por el gestor de ventanas de la ROM. Hay que comprobar ambas disposiciones en hardware real.

## Obtener el APK con GitHub Actions

Desde PowerShell, en el repositorio real (hay una carpeta contenedora con el mismo nombre):

```powershell
cd F:\Github\RGDS-Dashboard\RGDS-Dashboard
git status
git add .
git commit -m "Add read-only INFO diagnostics for RGDS Dashboard v0.2.0"
git push -u origin main
```

1. Abre [Actions del repositorio](https://github.com/Riv0Trill224/RGDS-Dashboard/actions).
2. Selecciona **Android APK** y la ejecución del push. También puedes pulsar **Run workflow** en la rama `main`.
3. Espera a que la compilación y la subida del APK finalicen correctamente. Tests y lint se ejecutan después y no bloquean la entrega del artifact.
4. En **Artifacts**, descarga **RGDS-Dashboard-v0.2** y extrae el ZIP.
5. Copia **RGDS-Dashboard-v0.2.apk** a la consola y ábrelo desde su gestor de archivos. Autoriza la instalación desde esa fuente si Android lo solicita.

El APK es debug, firmado automáticamente e instalable sin Play Store. No requiere secretos de firma. La clave debug puede variar entre ejecuciones limpias de CI; si Android rechaza una actualización por firma incompatible, desinstala la versión anterior (se perderán sus preferencias) o usa una clave de firma estable en una versión futura.

## Compilar sin Android Studio

Toolchain fijado: **AGP 8.7.3, Gradle 8.9, Java 17, compileSdk/targetSdk 35**, build tools 34.0.0. `minSdk 26` y APIs de interfaz nativas, sin AndroidX/Compose ni bibliotecas de ejecución externas. JUnit solo se usa en tests. El APK debug está destinado a instalación directa, no a publicación en Play Store.

Con Java 17 y Android command-line tools instalados (el wrapper incluido descarga Gradle 8.9):

```powershell
# Configura JAVA_HOME y ANDROID_HOME con tus rutas reales.
sdkmanager "platforms;android-35" "build-tools;34.0.0"
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Salida: `app/build/outputs/apk/debug/app-debug.apk`. El workflow renombra la copia descargable. No subas `local.properties`, `.tools`, claves ni directorios `build`.

Los tests de los scripts de diagnóstico usan un shell POSIX y árboles sysfs simulados, sin ejecutar `su`. En Ubuntu se utiliza `sh` del PATH. En Windows, para ejecutar esos tests, define `RGDS_TEST_SHELL` con la ruta a `sh.exe` de Git for Windows; `:app:assembleDebug` no necesita ese shell.

## Autenticación y privacidad

Esta aplicación no tiene login, backend, peticiones de red, contraseñas, tokens OAuth, anuncios, trackers ni telemetría. El manifiesto no declara permisos. El acceso al fondo es una concesión de Android limitada al documento elegido. Magisk administra por separado la autorización root opcional de INFO. El diagnóstico permanece en memoria y solo se copia al portapapeles por acción del usuario. Ver [flujo de permisos y credenciales](docs/AUTHENTICATION.md).

## Verificación

Los tests de CPU cubren intervalos reales, guest time sin doble conteo, iowait, datos ausentes/malformados y reinicio de contadores. CI reutiliza el SDK de `ubuntu-latest` e instala únicamente los paquetes necesarios que falten: `platform-tools`, `platforms;android-35` y `build-tools;34.0.0`. No instala el paquete obsoleto `tools`. Ejecuta `:app:assembleDebug`, publica el APK y después ejecuta tests/lint sin bloquear el artifact si fallan. Sigue [la lista de pruebas en consola](docs/TESTING.md) antes de considerar validado GammaOS.

Referencias oficiales: [compatibilidad AGP 8.7 / Gradle / JDK](https://developer.android.com/build/releases/agp-8-7-0-release-notes), [documentos y permisos persistentes](https://developer.android.com/training/data-storage/shared/documents-files), [ciclo de vida en múltiples ventanas](https://developer.android.com/develop/ui/views/layout/support-multi-window-mode).


## Proyecto abierto y comunidad

RGDS Dashboard se publica bajo **GNU GPL v3.0 o posterior** (`GPL-3.0-or-later`), salvo componentes de terceros con sus propios avisos. Puedes estudiar el código, modificarlo, hacer forks y redistribuirlo, también comercialmente, cumpliendo la licencia. Al distribuir versiones derivadas, conserva las libertades de la GPL y proporciona el código fuente correspondiente a quienes las reciben. Las modificaciones de uso privado no obligan a publicarlas.

Consulta [LICENSE](LICENSE) y [CONTRIBUTING.md](CONTRIBUTING.md). Son bienvenidos los reportes de errores, pruebas en otras consolas, traducciones, diseño y cambios mediante pull requests.

El código necesario para compilar Android y el futuro port Linux, los scripts de publicación y cualquier futuro servicio propio de reportes estarán abiertos. Las contraseñas, claves privadas de firma y registros personales no se publican. Los forks pueden generar su propia firma y configurar su propio repositorio de actualizaciones y destino de reportes; no requieren nuestra clave oficial.
