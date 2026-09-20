# Autenticación, permisos y credenciales

No hay autenticación de usuarios ni solicitudes a servidores. La app no contiene cliente HTTP, SDK de analítica ni permiso `INTERNET`. No almacena ni transmite contraseñas, API keys o tokens. No existía código previo en el repositorio vacío.

## Componentes

Código de referencia: [MainActivity.java](../app/src/main/java/com/rgds/dashboard/MainActivity.java), [AndroidManifest.xml](../app/src/main/AndroidManifest.xml), [StatsReader.java](../app/src/main/java/com/rgds/dashboard/StatsReader.java) y [workflow de Android](../.github/workflows/android.yml).

- `MainActivity`: inicia el selector del sistema y administra el permiso de documento.
- `ContentResolver`: abre la imagen y solicita/libera el permiso URI persistente.
- `SharedPreferences("dashboard", MODE_PRIVATE)`: almacena `wallpaper` (URI) y `dim` (0–90). El URI no es una credencial de login; Android controla quién puede abrirlo.
- `AndroidManifest.xml`: expone únicamente la actividad launcher, deshabilita backup y no declara permisos. No hay servicios exportados ni receptores registrados en el manifiesto.
- `UnavailableFpsProvider`: no enlaza con Shizuku ni solicita autorización.

## Flujo del wallpaper

```text
Usuario pulsa Elegir fondo
  → ACTION_OPEN_DOCUMENT + CATEGORY_OPENABLE + image/*
  → Selector Android: usuario elige un documento
  → onActivityResult: valida resultado y content:// URI
  → takePersistableUriPermission(URI, FLAG_GRANT_READ_URI_PERMISSION)
  → Worker abre la imagen con ContentResolver y reduce su resolución
  → Si decodifica: muestra imagen, guarda URI y libera la concesión anterior
  → Si falla: conserva el fondo anterior y libera la nueva concesión no guardada
```

En cada apertura se lee el URI guardado y se intenta abrir con la concesión persistente del sistema. Si el documento fue borrado/movido o el permiso revocado, se muestra un aviso y el fondo base; la actividad sigue funcionando. «Quitar fondo» elimina la preferencia y libera el permiso. No se necesitan permisos generales de almacenamiento, fotos o archivos.

## GitHub Actions

El workflow concede `contents: read` al token efímero del job para checkout. GitHub administra sus credenciales de ejecución y de subida de artifacts; no se copian al APK. La firma debug la genera el toolchain y solo identifica el paquete instalable: no autentica al usuario ni ofrece una identidad de distribución estable entre runners nuevos. No se han configurado secrets ni una clave de producción.

La futura autorización Shizuku será independiente del permiso del wallpaper y deberá ser opcional, revocable y tolerante a la ausencia del servicio.
