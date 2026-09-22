# Contribuir a RGDS Dashboard

Gracias por ayudar a construir un panel abierto para consolas de doble pantalla. Puedes contribuir con código, documentación, traducciones, diseño y pruebas en equipos reales.

## Reportar un problema

Abre una issue con versión exacta de la app, consola, Android/ROM, estado de root, juego o emulador, posición de cada pantalla, pasos para reproducirlo y resultado esperado/observado. Adjunta el diagnóstico cuando esté disponible y revisa su contenido antes de publicarlo. No incluyas contraseñas, tokens, claves de firma ni datos personales. Si un campo no aparece, indica cuál; no atribuyas el fallo a root sin comprobarlo.

Las sugerencias deben explicar qué problema resuelven y en qué consola se usarían. También aceptamos documentación y pruebas sin cambios de código.

## Enviar cambios

1. Haz un fork y crea una rama para un cambio concreto.
2. Sigue las instrucciones de compilación del README (Java 17, Gradle 8.9 y SDK indicado allí).
3. Ejecuta `./gradlew testDebugUnitTest lintDebug assembleDebug` o su equivalente `gradlew.bat` en Windows; los tests de scripts requieren el shell descrito en README.
4. Añade pruebas cuando el cambio afecte lecturas, permisos, procesos, selección de juego o actualizaciones. Documenta qué pudiste probar y qué queda pendiente en hardware.
5. Abre un pull request describiendo problema, solución, evidencia y límites.

Revisa [TESTING.md](docs/TESTING.md), [ARCHITECTURE.md](docs/ARCHITECTURE.md) y la especificación de la versión en desarrollo. Una compilación correcta no valida el comportamiento de todas las consolas. Las métricas sin fuente fiable deben indicar ausencia de datos; nunca sustituir FPS del juego por Hz o FPS del dashboard.

## Licencia y atribución

El código original del proyecto se ofrece bajo GNU GPL v3.0 o posterior (`GPL-3.0-or-later`), según LICENSE. Presenta contribuciones que puedas distribuir bajo esos términos y conserva los avisos de terceros. Identifica origen y licencia de nuevas dependencias o recursos. No se solicita cesión de la titularidad de tus aportes.

La licencia permite forks y distribución comercial. Al distribuir versiones modificadas, cumple la GPL, conserva los avisos y entrega el código fuente correspondiente a los destinatarios. No exige publicar cambios que solo utilizas de forma privada.

## Firmas, actualizaciones y forks

Para compilar localmente basta tu firma debug; para distribuir tu fork usa una clave privada propia y respáldala. La clave de las publicaciones oficiales no se comparte.

Antes de distribuir un fork, identifica claramente tu versión y revisa applicationId, nombre, repositorio de actualizaciones y destino de reportes. Cambiar applicationId permite instalarlo junto a la app oficial. Un APK firmado con otra clave no actualiza normalmente la instalación oficial aunque use el mismo paquete.

El actualizador y el envío de reportes aún están planificados. Su implementación deberá permitir configurar esos destinos durante la compilación y documentar cómo hacerlo. El futuro servicio de entrega, si se desarrolla, también tendrá su código publicado; las credenciales serán configuración privada del operador.

## Publicaciones

Cada APK público debe apuntar a su tag/commit y ofrecer el código fuente correspondiente, instrucciones de compilación, licencia y cambios conocidos. Las versiones experimentales se marcarán como pre-release. Las claves de publicación y los envíos oficiales de correo solo se usan en flujos de mantenimiento; los PR de forks no necesitan esos secretos.

## Trato en la comunidad

Se espera comunicación respetuosa y centrada en el proyecto. Explica desacuerdos técnicos con ejemplos y evita ataques personales. Las decisiones de integración se revisan mediante issues y pull requests.
