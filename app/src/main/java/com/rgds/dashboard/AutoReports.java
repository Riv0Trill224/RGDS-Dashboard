package com.rgds.dashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * RGDS Dashboard v0.5
 *
 * Gestiona la cola privada de diagnósticos y su envío
 * al Worker oficial de Cloudflare.
 *
 * La RGDS_API_KEY no forma parte del APK.
 * Puede importarse desde un archivo preparado en una PC.
 */
final class AutoReports {

    /**
     * Código utilizado por MainActivity para identificar
     * el resultado del selector de archivos.
     */
    static final int IMPORT_API_KEY = 43;

    private final Context context;
    private final SharedPreferences prefs;
    private final Supplier<SessionLog> source;

    private final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor();

    private final File queue;

    private volatile String state = "Autoenvío desactivado";

    private long lastSnapshot;

    AutoReports(
            Context context,
            Supplier<SessionLog> source
    ) {

        this.context =
                context.getApplicationContext();

        this.source = source;

        prefs =
                context.getSharedPreferences(
                        "private-report-relay",
                        0
                );

        queue =
                new File(
                        context.getFilesDir(),
                        "report-queue"
                );

        if (!queue.exists()) {
            queue.mkdirs();
        }

        /*
         * Intento periódico.
         *
         * El primer intento programado ocurre
         * cinco minutos después de abrir Dashboard
         * y posteriormente cada cinco minutos.
         */
        worker.scheduleWithFixedDelay(
                () -> captureAndSend(false),
                5,
                5,
                TimeUnit.MINUTES
        );

        /*
         * Si el usuario ya había activado
         * el envío anteriormente, se hace
         * también un intento al iniciar.
         */
        if (prefs.getBoolean("enabled", false)) {

            worker.execute(
                    () -> captureAndSend(true)
            );
        }
    }

    String status() {
        return state;
    }

    /**
     * Se llama cuando Dashboard deja de estar visible.
     *
     * Guarda y, cuando es posible, envía una última
     * instantánea del diagnóstico.
     */
    void checkpoint() {

        if (!worker.isShutdown()) {

            worker.execute(
                    () -> captureAndSend(false)
            );
        }
    }

    /**
     * Cierra el hilo dedicado al autoenvío.
     */
    void close() {
        worker.shutdown();
    }

    /**
     * Guarda una instantánea en la cola privada
     * y después intenta transmitir los reportes
     * pendientes al Worker.
     */
    private void captureAndSend(
            boolean force
    ) {

        if (!prefs.getBoolean("enabled", false)) {

            state = "Autoenvío desactivado";
            return;
        }

        try {

            long now =
                    android.os.SystemClock.elapsedRealtime();

            /*
             * GUARDAR ANTES DE ENVIAR
             *
             * De esta manera una pérdida de conexión
             * no destruye el diagnóstico.
             */
            if (force
                    || now - lastSnapshot >= 60000) {

                File[] existing =
                        queue.listFiles(
                                (dir, name) ->
                                        name.endsWith(".json")
                        );

                /*
                 * La cola está deliberadamente limitada
                 * para evitar crecimiento indefinido.
                 */
                if (existing != null
                        && existing.length >= 10) {

                    state =
                            "Cola llena (10); conserva LOG local";

                } else {

                    SessionLog log =
                            source.get();

                    if (log == null) {

                        state =
                                "Sin sesión disponible";

                        return;
                    }

                    String id =
                            UUID.randomUUID()
                                    .toString();

                    JSONObject body =
                            new JSONObject()
                                    .put(
                                            "schema",
                                            1
                                    )
                                    .put(
                                            "reportId",
                                            id
                                    )
                                    .put(
                                            "version",
                                            BuildConfig.VERSION_NAME
                                    )
                                    .put(
                                            "session",
                                            log.name()
                                    )
                                    .put(
                                            "log",
                                            new String(
                                                    log.snapshot(),
                                                    StandardCharsets.UTF_8
                                            )
                                    );

                    File temp =
                            new File(
                                    queue,
                                    id + ".tmp"
                            );

                    File ready =
                            new File(
                                    queue,
                                    id + ".json"
                            );

                    try (
                            FileOutputStream out =
                                    new FileOutputStream(temp)
                    ) {

                        out.write(
                                body.toString()
                                        .getBytes(
                                                StandardCharsets.UTF_8
                                        )
                        );

                        /*
                         * Intentamos que el archivo quede
                         * físicamente escrito antes de renombrarlo.
                         */
                        out.getFD().sync();
                    }

                    if (!temp.renameTo(ready)) {

                        throw new IOException(
                                "No se pudo guardar cola"
                        );
                    }

                    lastSnapshot = now;
                }
            }

            /*
             * v0.5 utiliza únicamente el Worker oficial.
             * La URL ya no puede modificarse desde la app.
             */
            String token =
                    prefs.getString(
                            "token",
                            ""
                    );

            if (token == null
                    || token.length() < 32) {

                state =
                        "Guardado: falta configurar RGDS_API_KEY";

                return;
            }

            URL url =
                    new URL(
                            ReportTransport.CLOUDFLARE
                    );

            /*
             * Protección adicional.
             */
            if (!"https".equalsIgnoreCase(
                    url.getProtocol()
            )
                    || url.getUserInfo() != null) {

                throw new IOException(
                        "Endpoint Cloudflare no válido"
                );
            }

            File[] files =
                    queue.listFiles(
                            (dir, name) ->
                                    name.endsWith(".json")
                    );

            if (files == null
                    || files.length == 0) {

                state =
                        "Sin reportes pendientes";

                return;
            }

            /*
             * Enviar primero los diagnósticos
             * más antiguos.
             */
            Arrays.sort(
                    files,
                    Comparator.comparingLong(
                            File::lastModified
                    )
            );

            for (File file : files) {

                /*
                 * Permite desactivar el envío
                 * mientras existe una cola pendiente.
                 */
                if (!prefs.getBoolean(
                        "enabled",
                        false
                )) {
                    return;
                }

                byte[] data =
                        Files.readAllBytes(
                                file.toPath()
                        );

                JSONObject report =
                        new JSONObject(
                                new String(
                                        data,
                                        StandardCharsets.UTF_8
                                )
                        );

                /*
                 * El Worker recibe solo los últimos
                 * 16 000 caracteres del LOG.
                 *
                 * El LOG completo continúa almacenado
                 * localmente en la consola.
                 */
                String original =
                        report.getString("log");

                String excerpt =
                        ReportTransport.excerpt(
                                original
                        );

                report.put(
                                "log",
                                excerpt
                        )
                        .put(
                                "logTruncated",
                                !original.equals(excerpt)
                        )
                        .put(
                                "originalLogBytes",
                                original.getBytes(
                                        StandardCharsets.UTF_8
                                ).length
                        );

                data =
                        report.toString()
                                .getBytes(
                                        StandardCharsets.UTF_8
                                );

                HttpURLConnection connection =
                        (HttpURLConnection)
                                url.openConnection();

                /*
                 * Importante:
                 * no seguir redirecciones automáticamente.
                 *
                 * Así evitamos reenviar accidentalmente
                 * Authorization a otro servidor.
                 */
                connection.setInstanceFollowRedirects(
                        false
                );

                connection.setConnectTimeout(
                        10000
                );

                connection.setReadTimeout(
                        20000
                );

                connection.setRequestMethod(
                        "POST"
                );

                connection.setDoOutput(
                        true
                );

                connection.setFixedLengthStreamingMode(
                        data.length
                );

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=utf-8"
                );

                connection.setRequestProperty(
                        "Authorization",
                        "Bearer " + token
                );

                try {

                    try (
                            OutputStream out =
                                    connection.getOutputStream()
                    ) {

                        out.write(data);
                    }

                    int responseCode =
                            connection.getResponseCode();

                    if (responseCode != 200) {

                        throw new IOException(
                                "HTTP " + responseCode
                        );
                    }

                    /*
                     * El acuse del Worker también
                     * tiene un límite de tamaño.
                     */
                    ByteArrayOutputStream response =
                            new ByteArrayOutputStream();

                    try (
                            InputStream in =
                                    connection.getInputStream()
                    ) {

                        byte[] buffer =
                                new byte[1024];

                        int count;

                        while (
                                (count =
                                        in.read(buffer))
                                        != -1
                        ) {

                            if (
                                    response.size()
                                            + count
                                            > 8192
                            ) {

                                throw new IOException(
                                        "Respuesta demasiado grande"
                                );
                            }

                            response.write(
                                    buffer,
                                    0,
                                    count
                            );
                        }
                    }

                    JSONObject ack =
                            new JSONObject(
                                    new String(
                                            response.toByteArray(),
                                            StandardCharsets.UTF_8
                                    )
                            );

                    /*
                     * Verificar que Cloudflare
                     * confirma exactamente el mismo
                     * reportId que enviamos.
                     */
                    boolean accepted =
                            ReportTransport.accepted(
                                    true,
                                    report.getString(
                                            "reportId"
                                    ),
                                    ack.optString(
                                            "reportId"
                                    ),
                                    ack.optString(
                                            "status"
                                    ),
                                    ack.optBoolean(
                                            "authenticated"
                                    )
                            );

                    if (!accepted) {

                        throw new IOException(
                                "Worker sin acuse autenticado"
                        );
                    }

                    /*
                     * Solo después de recibir
                     * confirmación correcta eliminamos
                     * el reporte de la cola.
                     */
                    if (!file.delete()) {

                        throw new IOException(
                                "No se pudo retirar reporte confirmado"
                        );
                    }

                    state =
                            "Cloudflare recibió diagnóstico correctamente";

                } finally {

                    connection.disconnect();
                }
            }

        } catch (Exception e) {

            String detail =
                    e.getClass()
                            .getSimpleName();

            if (e instanceof IOException
                    && e.getMessage() != null) {

                String message =
                        e.getMessage();

                if (
                        message.startsWith("HTTP ")
                                || message.startsWith(
                                        "Worker "
                                )
                                || message.startsWith(
                                        "Endpoint "
                                )
                ) {

                    detail = message;
                }
            }

            state =
                    "Pendiente; reintento automático en 5 min. "
                            + detail;
        }
    }

    /**
     * Importa RGDS_API_KEY desde un archivo seleccionado
     * con Android Storage Access Framework.
     *
     * Formatos admitidos:
     *
     * xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
     *
     * o:
     *
     * RGDS_API_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
     */
    boolean importKey(
            Uri uri
    ) {

        if (uri == null
                || !"content".equals(
                        uri.getScheme()
                )) {

            state =
                    "Archivo de API key no válido";

            return false;
        }

        try (
                InputStream input =
                        context.getContentResolver()
                                .openInputStream(uri)
        ) {

            if (input == null) {

                throw new IOException(
                        "No se pudo abrir archivo"
                );
            }

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            byte[] buffer =
                    new byte[512];

            int count;
            int total = 0;

            while (
                    (count =
                            input.read(buffer))
                            != -1
            ) {

                total += count;

                /*
                 * Un archivo de API key legítimo
                 * debe ser diminuto.
                 */
                if (total > 4096) {

                    throw new IOException(
                            "Archivo demasiado grande"
                    );
                }

                output.write(
                        buffer,
                        0,
                        count
                );
            }

            String secret =
                    new String(
                            output.toByteArray(),
                            StandardCharsets.UTF_8
                    );

            /*
             * Aceptar archivos UTF-8 creados
             * por editores que añaden BOM.
             */
            if (secret.startsWith("\uFEFF")) {

                secret =
                        secret.substring(1);
            }

            secret =
                    secret.trim();

            /*
             * Por comodidad también aceptamos:
             *
             * RGDS_API_KEY=clave
             */
            if (secret.startsWith(
                    "RGDS_API_KEY="
            )) {

                secret =
                        secret.substring(
                                "RGDS_API_KEY="
                                        .length()
                        ).trim();
            }

            /*
             * El Worker exige al menos
             * 32 caracteres.
             *
             * También fijamos un máximo
             * razonable.
             */
            if (secret.length() < 32
                    || secret.length() > 512) {

                state =
                        "API key inválida";

                return false;
            }

            /*
             * No permitir espacios,
             * tabs o saltos de línea
             * dentro de la key.
             */
            for (
                    int i = 0;
                    i < secret.length();
                    i++
            ) {

                if (
                        Character.isWhitespace(
                                secret.charAt(i)
                        )
                ) {

                    state =
                            "API key inválida";

                    return false;
                }
            }

            /*
             * Copiar el secreto al almacenamiento
             * privado de la aplicación.
             *
             * El archivo externo deja de ser
             * necesario después.
             */
            prefs.edit()
                    .putString(
                            "token",
                            secret
                    )
                    /*
                     * Limpiar cualquier URL antigua
                     * almacenada por v0.4.
                     */
                    .remove("endpoint")
                    .apply();

            state =
                    "API key importada correctamente";

            /*
             * Si el autoenvío ya estaba activo,
             * probar inmediatamente Cloudflare.
             */
            if (
                    prefs.getBoolean(
                            "enabled",
                            false
                    )
                            && !worker.isShutdown()
            ) {

                worker.execute(
                        () -> captureAndSend(true)
                );
            }

            return true;

        } catch (Exception e) {

            state =
                    "No se pudo importar API key: "
                            + e.getClass()
                                    .getSimpleName();

            return false;
        }
    }

    /**
     * Pantalla de configuración del sistema
     * de diagnóstico automático.
     */
    void configure(
            Activity activity
    ) {

        LinearLayout form =
                new LinearLayout(activity);

        form.setOrientation(
                LinearLayout.VERTICAL
        );

        form.setPadding(
                24,
                12,
                24,
                12
        );

        /*
         * ACTIVAR / DESACTIVAR
         */
        Switch enabled =
                new Switch(activity);

        enabled.setText(
                "Enviar diagnósticos automáticamente"
        );

        enabled.setChecked(
                prefs.getBoolean(
                        "enabled",
                        false
                )
        );

        form.addView(enabled);

        /*
         * SERVIDOR
         */
        TextView server =
                new TextView(activity);

        server.setText(
                "Servidor:\n"
                        + ReportTransport.CLOUDFLARE
        );

        server.setPadding(
                0,
                16,
                0,
                8
        );

        form.addView(server);

        /*
         * ESTADO DE API KEY
         */
        String currentToken =
                prefs.getString(
                        "token",
                        ""
                );

        TextView keyStatus =
                new TextView(activity);

        if (
                currentToken != null
                        && currentToken.length()
                        >= 32
        ) {

            keyStatus.setText(
                    "API key: configurada ✓"
            );

        } else {

            keyStatus.setText(
                    "API key: no configurada"
            );
        }

        keyStatus.setPadding(
                0,
                12,
                0,
                8
        );

        form.addView(keyStatus);

        /*
         * IMPORTAR DESDE ARCHIVO
         */
        Button importButton =
                new Button(activity);

        importButton.setText(
                "Importar API key desde archivo"
        );

        importButton.setAllCaps(false);

        importButton.setOnClickListener(
                view -> {

                    Intent intent =
                            new Intent(
                                    Intent.ACTION_OPEN_DOCUMENT
                            );

                    intent.addCategory(
                            Intent.CATEGORY_OPENABLE
                    );

                    /*
                     * Normalmente será .txt.
                     */
                    intent.setType(
                            "text/plain"
                    );

                    intent.addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );

                    try {

                        activity.startActivityForResult(
                                intent,
                                IMPORT_API_KEY
                        );

                    } catch (
                            RuntimeException e
                    ) {

                        Toast.makeText(
                                activity,
                                "No hay un selector de archivos disponible.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );

        form.addView(
                importButton
        );

        /*
         * INSTRUCCIONES
         */
        TextView instructions =
                new TextView(activity);

        instructions.setPadding(
                0,
                12,
                0,
                0
        );

        instructions.setText(
                "Desde tu PC crea RGDS_API_KEY.txt y coloca únicamente "
                        + "la clave del Worker.\n\n"
                        + "Copia el archivo a la consola o microSD y selecciona "
                        + "\"Importar API key desde archivo\".\n\n"
                        + "Dashboard copiará la clave a su almacenamiento privado. "
                        + "Después puedes borrar RGDS_API_KEY.txt."
        );

        form.addView(
                instructions
        );

        /*
         * ESTADO Y PRIVACIDAD
         */
        TextView note =
                new TextView(activity);

        note.setPadding(
                0,
                16,
                0,
                0
        );

        note.setText(
                "Dashboard enviará un extracto del diagnóstico "
                        + "al iniciar la app, aproximadamente cada 5 minutos "
                        + "y cuando deje de estar visible.\n\n"
                        + "El LOG completo permanece almacenado localmente "
                        + "en la consola.\n\n"
                        + "Cloudflare Observability recibe el extracto; "
                        + "no envía correo.\n\n"
                        + "Estado actual: "
                        + state
        );

        form.addView(
                note
        );

        ScrollView scroll =
                new ScrollView(activity);

        scroll.addView(
                form
        );

        /*
         * DIÁLOGO
         */
        new AlertDialog.Builder(activity)

                .setTitle(
                        "Diagnósticos automáticos"
                )

                .setView(
                        scroll
                )

                .setPositiveButton(
                        "Guardar",
                        (dialog, which) -> {

                            String secret =
                                    prefs.getString(
                                            "token",
                                            ""
                                    );

                            /*
                             * No permitir activar el sistema
                             * sin una API key válida.
                             */
                            if (
                                    enabled.isChecked()
                                            && (
                                            secret == null
                                                    || secret.length()
                                                    < 32
                                    )
                            ) {

                                Toast.makeText(
                                        activity,
                                        "Primero importa RGDS_API_KEY.txt",
                                        Toast.LENGTH_LONG
                                ).show();

                                prefs.edit()
                                        .putBoolean(
                                                "enabled",
                                                false
                                        )
                                        .apply();

                                state =
                                        "Falta configurar RGDS_API_KEY";

                                return;
                            }

                            prefs.edit()
                                    .putBoolean(
                                            "enabled",
                                            enabled.isChecked()
                                    )
                                    /*
                                     * Eliminar la antigua configuración
                                     * de endpoint de v0.4.
                                     */
                                    .remove(
                                            "endpoint"
                                    )
                                    .apply();

                            if (
                                    enabled.isChecked()
                            ) {

                                state =
                                        "Preparando primer envío…";

                                /*
                                 * Primer envío inmediato.
                                 */
                                if (!worker.isShutdown()) {

                                    worker.execute(
                                            () ->
                                                    captureAndSend(
                                                            true
                                                    )
                                    );
                                }

                            } else {

                                state =
                                        "Autoenvío desactivado";
                            }
                        }
                )

                .setNeutralButton(
                        "Estado",
                        (dialog, which) ->

                                Toast.makeText(
                                        activity,
                                        state,
                                        Toast.LENGTH_LONG
                                ).show()
                )

                .setNegativeButton(
                        "Cancelar",
                        null
                )

                .show();
    }
}
