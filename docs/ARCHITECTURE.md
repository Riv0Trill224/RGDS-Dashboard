# Componentes y flujo

```text
MainActivity.onStart
  └─ ScheduledExecutorService (~1 s, una tarea a la vez)
       ├─ StatsReader → BatteryManager / MemoryInfo / /proc/stat / thermal / broadcast
       │    └─ CpuUsage → diferencias de contadores de dos muestras
       ├─ FpsProvider → UnavailableFpsProvider
       └─ Handler (main) → DashboardView.update → Canvas escalado
MainActivity.onStop → invalida callbacks pendientes y detiene worker
```

`DashboardView` dibuja sobre un lienzo lógico 640×400 centrado y escalado uniformemente; los controles nativos ocupan 48 píxeles al pie de la ventana. `ImageView.CENTER_CROP` ocupa todo el fondo y una capa negra aplica el porcentaje elegido. El dashboard respeta el tamaño del display que aloje la actividad sin consultar un display principal fijo.

Cada muestreo crea un snapshot nuevo: las fuentes fallidas muestran ausencia de datos. `CpuUsage` suma user, nice, system, idle, iowait, irq, softirq y steal; guest/guest_nice ya están incluidos en otros contadores. El porcentaje es `(deltaTotal - deltaIdle - deltaIowait) / deltaTotal`. Intervalos no válidos y reinicios no producen porcentajes inventados.

Las zonas térmicas se inspeccionan individualmente; se muestra la más caliente dentro del rango plausible -40…150 °C junto a su tipo. No equivale necesariamente a temperatura de CPU. Los sensores de batería usan décimas de grado y rango -40…120 °C. Los errores de permisos, archivos ausentes, formatos inválidos y fallos de servicios se aíslan por fuente.

## Contrato futuro de FPS

`FpsProvider.Reading` contiene un FPS nullable y un estado visible. `sample()` se invoca fuera del hilo principal; `close()` permite liberar recursos. La implementación v0.1 no usa Choreographer, SurfaceView ni timestamps del dashboard.

Para Shizuku:

1. Crear un proveedor opcional con servicio/binder y permiso solicitado solo cuando el usuario active la función.
2. Mantener una selección explícita de display y capa del juego, distinta del display/capa del dashboard; volver a resolverla al cambiar de pantallas o aplicación.
3. Consultar capacidades de SurfaceFlinger: la disponibilidad de comandos y campos depende de la ROM. Usar lecturas acotadas, sin comandos shell construidos con nombres de aplicaciones no escapados.
4. Calcular FPS únicamente con nuevos timestamps reales de presentación en una ventana de tiempo documentada. Manejar timestamps duplicados, cero, obsoletos y ausencia de frames.
5. Ante permiso ausente, muerte del binder, capa perdida o ROM incompatible, devolver FPS null y estado útil. No sustituirlo por tasa de refresco del display.
6. Inyectar el proveedor en `MainActivity` y añadir ajustes de autorización/selección. `DashboardView` puede conservar su contrato de renderizado.

El proveedor actual no necesita conocer displays porque siempre devuelve ausencia de FPS; la selección de destino pertenecerá a la implementación/ajustes de la etapa Shizuku.
