# Compilación de prueba con firma estable

El workflow `Signed test APK` usa cuatro secretos de repositorio:
`RGDS_KEYSTORE_BASE64`, `RGDS_KEYSTORE_PASSWORD`, `RGDS_KEY_ALIAS` y
`RGDS_KEY_PASSWORD`. El alias configurado por el mantenedor es `rgds-release`.
Base64 no cifra la clave: nunca se debe guardar en Git ni compartir en incidencias.

Tras integrar el workflow en `main`, abrir **Actions → Signed test APK → Run workflow**
y seleccionar `main`. Ejecuta pruebas unitarias y lint como requisitos obligatorios,
compila, verifica la firma con apksigner y exige la huella SHA-256 del certificado:

`1C:09:51:5E:E9:23:D5:61:0D:C4:1E:85:D7:5A:49:90:0C:10:15:8B:18:C4:70:34:63:89:6E:4F:D1:B1:02:64`

Un resultado exitoso proporciona un artefacto ZIP con APK, `SHA256SUMS.txt` y `update.json`.
El checksum verifica el archivo; la huella anterior identifica el certificado.
La clave temporal se elimina incluso si falla la compilación. No se usan secretos
en pull requests. Desde v0.4, un cambio de `app/build.gradle` integrado en main
ejecuta firma y publicación de una pre-release. También se puede ejecutar manualmente.
El job de publicación recibe solo el artefacto y permiso contents:write; no recibe claves.
La Release se crea como borrador y se hace pública tras cargar todos los archivos.
No se sobrescriben versiones publicadas: subir versionCode y versionName para otro test.

La versión procede de `app/build.gradle`. Compilar correctamente no demuestra que
el panel funcione en la consola ni que la actualización entre dos APK haya sido probada.

Si la APK instalada usa la firma de depuración, Android rechazará actualizarla con
esta firma. Antes de desinstalarla, exportar cualquier dato necesario; desinstalar
borra sus datos. Las siguientes versiones deben conservar clave y applicationId
y aumentar versionCode. Guardar una copia privada del almacén y sus contraseñas.

Los forks pueden compilar debug sin estos secretos. Para distribuir su propia
versión firmada deberán usar su propia clave y adaptar la huella esperada;
esa versión no podrá actualizar la distribución oficial manteniendo su firma.
