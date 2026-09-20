# RGDS Dashboard · 0.1.0

Dashboard Android para Anbernic RG DS con GammaOS Lite 1.2.2. Diseñado para una ventana horizontal de aproximadamente **640×480 píxeles**, en cualquiera de sus dos pantallas. Paquete `com.rgds.dashboard`, Android mínimo 8.0 (API 26). No necesita root, Shizuku ni Android Studio.

## Qué incluye

- Hora y fecha según la configuración del dispositivo.
- Batería mediante `BatteryManager`; RAM utilizada/total mediante `ActivityManager.MemoryInfo`.
- CPU global mediante diferencias de contadores de `/proc/stat`, si el sistema permite leerlo. La primera muestra y las lecturas inválidas muestran «No disponible».
- Temperatura de la zona térmica accesible más caliente, identificada por su `type`; no se presume que sea CPU. Se interpreta `temp` en miligrados Celsius, según la interfaz Linux thermal.
- Temperatura de batería mediante el broadcast persistente `ACTION_BATTERY_CHANGED`.
- Wallpaper `centerCrop`, selector de documentos Android, permiso URI persistente y oscurecimiento de 0–90%, guardado en preferencias privadas.
- Interfaz oscura original, botones enfocables con cruceta, landscape, modo inmersivo y pantalla encendida mientras la actividad está visible.

CPU, sensores y servicios son best-effort. Cada fuente falla de forma independiente y se vuelve a intentar; no se conserva una lectura antigua como si fuera actual. Un único worker toma muestras aproximadamente cada segundo, sin bloquear la interfaz. Los fondos se decodifican en otro worker, con reducción de resolución para limitar memoria.

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
git commit -m "Create RGDS Dashboard Android v0.1.0"
git push -u origin main
```

1. Abre [Actions del repositorio](https://github.com/Riv0Trill224/RGDS-Dashboard/actions).
2. Selecciona **Android APK** y la ejecución del push. También puedes pulsar **Run workflow** en la rama `main`.
3. Espera a que tests, lint y compilación finalicen correctamente.
4. En **Artifacts**, descarga **RGDS-Dashboard-v0.1** y extrae el ZIP.
5. Copia **RGDS-Dashboard-v0.1.apk** a la consola y ábrelo desde su gestor de archivos. Autoriza la instalación desde esa fuente si Android lo solicita.

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

## Autenticación y privacidad

El repositorio original estaba vacío: no había un sistema de autenticación que auditar. Esta aplicación no tiene login, backend, peticiones de red, contraseñas, tokens OAuth, anuncios, trackers ni telemetría. El manifiesto no declara permisos. El acceso al fondo es una concesión de Android limitada al documento elegido. Ver [flujo de permisos y credenciales](docs/AUTHENTICATION.md).

## Verificación

Los tests de CPU cubren intervalos reales, guest time sin doble conteo, iowait, datos ausentes/malformados y reinicio de contadores. CI ejecuta `testDebugUnitTest`, `lintDebug` y `assembleDebug`. Sigue [la lista de pruebas en consola](docs/TESTING.md) antes de considerar validado GammaOS.

Referencias oficiales: [compatibilidad AGP 8.7 / Gradle / JDK](https://developer.android.com/build/releases/agp-8-7-0-release-notes), [documentos y permisos persistentes](https://developer.android.com/training/data-storage/shared/documents-files), [ciclo de vida en múltiples ventanas](https://developer.android.com/develop/ui/views/layout/support-multi-window-mode).
