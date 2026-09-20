# Validación en consola

## Comprobaciones locales realizadas (2026-09-20)

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
