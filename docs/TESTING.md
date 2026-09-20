# Validación en consola

## Comprobaciones locales v0.2.0 (2026-09-20)

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`: BUILD SUCCESSFUL.
- 18 tests aprobados: CPU (3), interpretación térmica/Minecraft (4), procesos/denegación/timeout/interrupción/captura concurrente (6), scripts reales contra sysfs simulado (5).
- Los tests de procesos ejecutan una JVM de prueba, no `su`. Los de scripts usan un shell POSIX y directorios temporales; no leen ni modifican el sysfs real del host.
- Lint: 0 errores; advertencias de landscape deliberado en ambas actividades y sugerencia tipográfica de puntos suspensivos.
- `actionlint`: workflow válido. Comparado con la versión funcional: solo cambian los nombres APK/artifact de v0.1 a v0.2.
- `apksigner verify --min-sdk-version 26`: firma debug v2 verificada. `aapt`: paquete `com.rgds.dashboard`, versionCode 2, versionName 0.2.0, minSdk 26, targetSdk/compileSdk 35.
- Manifiesto final revisado: DiagnosticActivity declarada, no exportada, landscape; sin permisos adicionales. Java 8, AGP 8.7.3, Gradle 8.9 y JDK de compilación 17 conservados.
- El dashboard solo añade INFO y cambia V0.1 por V0.2. FpsProvider/UnavailableFpsProvider, lecturas periódicas y wallpaper no se modificaron.
- Pendiente: instalación/validación visual de v0.2 y autorización Magisk real en la consola. Ver [pruebas de INFO](DIAGNOSTICS.md#pruebas-en-rg-ds). La validación visual 640×480 se revisó en el código: dos filas de acciones y zona ScrollView con altura flexible; no se ejecutó un emulador.

## Comprobaciones locales v0.1.0 (2026-09-20)

- Compilación real con Java 17, Gradle 8.9, AGP 8.7.3 y SDK 35, sin Android Studio.
- `testDebugUnitTest`: 3 tests, 0 fallos, 0 errores.
- `lintDebug` y `assembleDebug`: completados. La orientación landscape es intencional para esta consola.
- Firma debug APK comprobada con `apksigner verify --min-sdk-version 26`.
- Metadatos inspeccionados con `aapt`: `com.rgds.dashboard`, versión 0.1.0, minSdk 26, targetSdk 35 y ausencia de permisos declarados.
- No se ha instalado ni ejecutado la app en una RG DS o emulador durante estas comprobaciones. El comportamiento visual, sensores y gestor multipantalla requieren las pruebas siguientes.

## Pruebas de hardware pendientes

No sustituir estas pruebas por un build exitoso. Se necesita la RG DS real para confirmar el comportamiento de GammaOS y de su gestor de dos pantallas.

1. Instalar sin root y sin Shizuku. Confirmar apertura horizontal e interfaz legible a 640×480.
2. Juego arriba/dashboard abajo y luego al revés. Confirmar que hora y métricas cambian mientras el juego tiene foco; ocultar el dashboard y comprobar que se detiene el muestreo.
3. Verificar batería y RAM frente a Android. CPU debe dar una primera muestra ausente y, si `/proc/stat` es accesible, valores después del siguiente intervalo; si no lo es, mostrar «No disponible» indefinidamente sin cierres.
4. Confirmar que `-- FPS` permanece así aun con el juego y animaciones en ejecución. El juego activo debe indicar ausencia de identificación, sin atribuir el launcher/dashboard como juego.
5. Verificar que la temperatura tiene nombre de sensor y que batería aparece por separado. Ausencia de `/sys` o sensores accesibles no debe impedir otras métricas.
6. Elegir una imagen grande, vertical y horizontal. Confirmar recorte centrado, cancelación sin cambios y selector ausente/malfuncionante sin cierre.
7. Cambiar oscuridad a 0%, 50% y 90%. Cerrar, forzar detención y reiniciar la consola: conservar URI y nivel. Revocar acceso o borrar el documento: mantener dashboard utilizable y permitir elegir otro.
8. Cambiar repetidamente de fondo y rotar/recrear actividad durante carga; no mostrar resultados de peticiones viejas. «Quitar fondo» vuelve al fondo base y no cambia el nivel de oscuridad.
9. Probar botones y diálogo con cruceta, confirmar entrada/salida del modo inmersivo y evitar suspensión con la actividad visible.
10. Comprobar instalación del APK descargado de Actions. Si una compilación posterior tiene otra clave debug, documentar la reinstalación necesaria.
