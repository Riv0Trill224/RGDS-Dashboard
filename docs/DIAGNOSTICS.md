# INFO / DIAGNÓSTICO · v0.2.0

El dashboard existente sigue sin necesitar root. INFO usa un ScrollView vertical, texto seleccionable, controles fijos, fullscreen y `FLAG_KEEP_SCREEN_ON`; no asume pantalla superior/inferior ni display ID 0. El display actual se obtiene de `getWindow().getDecorView().getDisplay()` después de adjuntar la ventana. El informe identifica el ID **al recopilar**, puesto que puede moverse después.

## Fuentes

| Sección RAW | Fuente |
| --- | --- |
| DEVICE | Build.MANUFACTURER, MODEL, DEVICE, BOARD, HARDWARE, VERSION.RELEASE, VERSION.SDK_INT, DISPLAY, os.version |
| DISPLAYS | DisplayManager; ID, nombre, resolución del modo, tamaño reportado, refresh rate, state y flags |
| ACTIVITYMANAGER API | MemoryInfo, procesos visibles por API y tareas propias; alcance restringido en Android |
| ROOT | `id`, mediante `su -c id` únicamente tras AUTORIZAR ROOT |
| KERNEL | `uname -a` |
| CPUINFO | `cat /proc/cpuinfo` |
| MEMINFO | `cat /proc/meminfo` completo en RAW |
| SURFACEFLINGER | `dumpsys SurfaceFlinger --list`, sin filtrar Minecraft |
| DISPLAY DUMPSYS | `dumpsys display` |
| ACTIVITY DUMPSYS | `dumpsys activity activities` |
| WINDOW DUMPSYS | `dumpsys window windows` |
| THERMAL | `/sys/class/thermal/thermal_zone*/type` y `temp` |
| CPU FREQUENCIES | `cpu[0-9]*/cpufreq`: scaling_cur_freq, scaling_min_freq, scaling_max_freq, cpuinfo_cur_freq, cpuinfo_max_freq |
| DEVFREQ | `/sys/class/devfreq/*`: name, cur_freq, min_freq, max_freq, available_frequencies, load, utilization cuando existen |
| GPU PLATFORM SEARCH | Búsqueda acotada de nombres GPU/Mali en `/sys/devices/platform`; mismos atributos disponibles |
| THERMAL INTERPRETADO | RAW y conversión conservadora de enteros en miligrados |
| MINECRAFT | Coincidencias/evidencias en SurfaceFlinger, activity y window |

Los comandos también se intentan sin root para registrar lo que la ROM permite y los motivos de rechazo. Cada uno incluye modo, comando, stdout, stderr, exitCode, timeout, interrupción y truncado si corresponde. Los fallos de archivos individuales quedan en stderr, conservando los archivos que sí se pudieron leer. No se modifica ninguna de estas fuentes.

## Interpretación prudente

- El refresh rate de un display **no es FPS**. El dashboard sigue mostrando `-- FPS`.
- THERMAL no atribuye ningún índice a CPU. Conserva la temperatura RAW; convierte enteros compatibles con miligrados en -40…150 °C, dejando sin convertir valores pequeños ambiguos, valores fuera de rango o formatos desconocidos.
- cpufreq muestra valores RAW en kHz, según la interfaz cpufreq. Devfreq conserva RAW; no interpreta `load` o `utilization` como porcentajes sin conocer el driver.
- Minecraft detectado significa que aparece el paquete exacto `com.mojang.minecraftpe`. Puede ser una tarea histórica o surface todavía registrada; no demuestra foco, visibilidad ni ejecución actual.
- Un PID o Display ID solo se extrae de una línea que contiene explícitamente el paquete y el campo correspondiente, o de un ProcessRecord con PID y paquete. No se toma el ID de una ventana cercana. Ante formatos nuevos se deja `--` y se conserva el RAW para análisis humano. Si hay varios IDs, se muestran todos sin elegir arbitrariamente uno.
- `NO` sin acceso completo significa únicamente sin coincidencias en lo capturado; no descarta Minecraft en la otra pantalla.

## Límites y ciclo de vida

- Autorización inicial: 30 s. Cada comando: 6 s. La interfaz sigue respondiendo mientras se espera a Magisk o a sysfs.
- stdout y stderr se drenan concurrentemente para evitar bloqueos por buffers llenos. Máximo 512 KiB por stream/comando; al excederse se sigue drenando y se marca TRUNCADO, nunca se presenta como salida completa.
- Enumeración térmica, CPU y devfreq: hasta 128 nodos por categoría, sin asumir cuántos hay. El límite queda indicado en RAW si se alcanza.
- Búsqueda de GPU: profundidad máxima 3, hasta 256 directorios y 32 coincidencias. No hace `find /sys` ni sigue recursivamente enlaces simbólicos en platform.
- ACTUALIZAR/ROOT/COPIAR se deshabilitan mientras se recopila, impidiendo instantáneas concurrentes o copias de información anterior. Cada actualización crea un informe nuevo con fecha, sin mezclar resultados antiguos.
- El resumen puede abreviar texto. RAW conserva las salidas capturadas y las muestra en páginas de 16.000 caracteres; copiar usa el informe, no la página visible.
- Android limita las transacciones Binder del portapapeles. Hasta 180.000 caracteres se copia en una sola operación con «Diagnóstico copiado». Para informes mayores se ofrece copia por partes consecutivas, sin quitar contenido: copiar/pegar cada parte una vez en orden.
- La aplicación no cancela el trabajo al perder foco en la otra pantalla ni al mostrar Magisk. Back/VOLVER cancela al destruir INFO. Se termina solo el subproceso propio de diagnóstico, nunca Minecraft, SurfaceFlinger u otros procesos del dispositivo.

## Pruebas en RG DS

1. Abrir INFO sin root y confirmar datos de Build/displays, errores de permisos registrados, texto seleccionable y Back funcional.
2. Pulsar AUTORIZAR ROOT y **rechazar** en Magisk: comprobar NO AUTORIZADO y que se completa el informe sin privilegios. Si Magisk recuerda la negativa, modificar esa decisión manualmente en Magisk para el siguiente caso.
3. Autorizar y comprobar que ROOT contiene `uid=0`, además de salidas SurfaceFlinger/display/activity/window completas dentro de los límites documentados.
4. Abrir Minecraft arriba/dashboard abajo, actualizar y copiar RAW. Intercambiar pantallas, repetir, y comparar los IDs de la ventana INFO con los dumps. No suponer que Minecraft siempre usa el ID opuesto o que solo existen dos displays.
5. Actualizar varias veces rápidamente: no abrir varias peticiones su ni mezclar resultados. No responder a la autorización durante 30 s: registrar timeout y continuar.
6. Regresar con Back durante la recopilación y abrir INFO otra vez: sin callbacks a la actividad destruida ni bloqueos. Probar controles con cruceta y ScrollView en 640×480.
7. Copiar con RAW paginado: el texto debe incluir todas las secciones, no solo la página actual. Si ofrece partes, concatenarlas en orden para recuperar el informe completo.
8. Verificar regresión del dashboard: wallpaper, oscurecimiento, estadísticas, landscape, `-- FPS` y ambas disposiciones de pantalla.

Referencias: [Process y timeouts](https://developer.android.com/reference/java/lang/Process), [Display](https://developer.android.com/reference/android/view/Display), [thermal sysfs](https://docs.kernel.org/5.10/driver-api/thermal/sysfs-api.html).
