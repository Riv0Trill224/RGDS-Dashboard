package com.rgds.dashboard;

import android.app.*;
import android.content.*;
import android.text.InputType;
import android.widget.*;

import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Cola privada de diagnósticos para RGDS Dashboard.
 *
 * El Worker de Cloudflare está fijado en ReportTransport.CLOUDFLARE.
 * La RGDS_API_KEY nunca forma parte del APK: el usuario la introduce
 * una sola vez y se guarda en SharedPreferences privados de la app.
 */
final class AutoReports {

    private final Context context;
    private final SharedPreferences prefs;
    private final Supplier<SessionLog> source;

    private final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor();

    private final File queue;

    private volatile String state = "Autoenvío desactivado";
    private long lastSnapshot;

    AutoReports(Context context, Supplier<SessionLog> source) {
        this.context = context.getApplicationContext();
        this.source = source;

        prefs = context.getSharedPreferences("private-report-relay", 0);

        queue = new File(context.getFilesDir(), "report-queue");
        queue.mkdirs();

        // Durante el desarrollo intenta enviar cada 5 minutos.
        worker.scheduleWithFixedDelay(
                () -> captureAndSend(false),
                5,
                5,
                TimeUnit.MINUTES
        );

        // Si ya estaba activado, intenta enviar al iniciar la app.
        if (prefs.getBoolean("enabled", false)) {
            worker.execute(() -> captureAndSend(true));
        }
    }

    String status() {
        return state;
    }

    /**
     * Se usa cuando Dashboard deja de estar visible.
     * Permite guardar/enviar una última instantánea.
     */
    void checkpoint() {
        if (!worker.isShutdown()) {
            worker.execute(() -> captureAndSend(false));
        }
    }

    void close() {
        worker.shutdown();
    }

    private void captureAndSend(boolean force) {

        if (!prefs.getBoolean("enabled", false)) {
            state = "Autoenvío desactivado";
            return;
        }

        try {
            long now = android.os.SystemClock.elapsedRealtime();

            /*
             * Guardar primero en almacenamiento privado.
             *
             * Evitamos generar duplicados si Android abre diálogos,
             * selector de archivos, instalador, etc.
             */
            if (force || now - lastSnapshot >= 60000) {

                File[] existing =
                        queue.listFiles((dir, name) -> name.endsWith(".json"));

                if (existing != null && existing.length >= 10) {
                    state = "Cola llena (10); conserva LOG local";
                } else {

                    SessionLog log = source.get();

                    if (log == null) {
                        state = "Sin sesión disponible";
                        return;
                    }

                    String id = UUID.randomUUID().toString();

                    JSONObject body = new JSONObject()
                            .put("schema", 1)
                            .put("reportId", id)
                            .put("version", BuildConfig.VERSION_NAME)
                            .put("session", log.name())
                            .put(
                                    "log",
                                    new String(
                                            log.snapshot(),
                                            StandardCharsets.UTF_8
                                    )
                            );

                    File temp = new File(queue, id + ".tmp");
                    File ready = new File(queue, id + ".json");

                    try (FileOutputStream out =
                                 new FileOutputStream(temp)) {

                        out.write(
                                body.toString()
                                        .getBytes(StandardCharsets.UTF_8)
                        );

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
             * La URL ya no es configurable.
             * v0.5 siempre usa el Worker oficial.
             */
            String token = prefs.getString("token", "");

            if (token == null || token.length() < 32) {
                state = "Guardado: falta configurar RGDS_API_KEY";
                return;
            }

            URL url = new URL(ReportTransport.CLOUDFLARE);

            if (!"https".equalsIgnoreCase(url.getProtocol())
                    || url.getUserInfo() != null) {

                throw new IOException(
                        "Endpoint Cloudflare no válido"
                );
            }

            File[] files =
                    queue.listFiles((dir, name) -> name.endsWith(".json"));

            if (files == null || files.length == 0) {
                state = "Sin reportes pendientes";
                return;
            }

            Arrays.sort(
                    files,
                    Comparator.comparingLong(File::lastModified)
            );

            for (File file : files) {

                if (!prefs.getBoolean("enabled", false)) {
                    return;
                }

                byte[] data = Files.readAllBytes(file.toPath());

                JSONObject report =
                        new JSONObject(
                                new String(
                                        data,
                                        StandardCharsets.UTF_8
                                )
                        );

                /*
                 * Cloudflare recibe solo un extracto reciente.
                 * El LOG completo permanece local.
                 */
                String original = report.getString("log");
                String excerpt =
                        ReportTransport.excerpt(original);

                report.put("log", excerpt)
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

                data = report.toString()
                        .getBytes(StandardCharsets.UTF_8);

                HttpURLConnection connection =
                        (HttpURLConnection) url.openConnection();

                /*
                 * Nunca reenviar Authorization automáticamente
                 * hacia otro host.
                 */
                connection.setInstanceFollowRedirects(false);

                connection.setConnectTimeout(10000);
                connection.setReadTimeout(20000);

                connection.setRequestMethod("POST");
                connection.setDoOutput(true);

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

                    try (OutputStream out =
                                 connection.getOutputStream()) {
                        out.write(data);
                    }

                    int responseCode =
                            connection.getResponseCode();

                    if (responseCode != 200) {
                        throw new IOException(
                                "HTTP " + responseCode
                        );
                    }

                    ByteArrayOutputStream response =
                            new ByteArrayOutputStream();

                    try (InputStream in =
                                 connection.getInputStream()) {

                        byte[] buffer = new byte[1024];
                        int count;

                        while ((count = in.read(buffer)) != -1) {

                            if (response.size() + count > 8192) {
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
                     * El Worker nuevo debe devolver exactamente
                     * el reportId enviado y confirmar autenticación.
                     */
                    if (!ReportTransport.accepted(
                            true,
                            report.getString("reportId"),
                            ack.optString("reportId"),
                            ack.optString("status"),
                            ack.optBoolean("authenticated")
                    )) {

                        throw new IOException(
                                "Worker sin acuse autenticado"
                        );
                    }

                    /*
                     * Solo retiramos el reporte de la cola después
                     * de recibir confirmación válida del Worker.
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

            String detail = e.getClass().getSimpleName();

            if (e instanceof IOException
                    && e.getMessage() != null) {

                String message = e.getMessage();

                if (message.startsWith("HTTP ")
                        || message.startsWith("Worker ")
                        || message.startsWith("Endpoint ")) {
                    detail = message;
                }
            }

            state =
                    "Pendiente; reintento automático en 5 min. "
                            + detail;
        }
    }

    void configure(Activity activity) {

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

        Switch enabled =
                new Switch(activity);

        enabled.setText(
                "Enviar diagnósticos automáticamente"
        );

        enabled.setChecked(
                prefs.getBoolean("enabled", false)
        );

        form.addView(enabled);

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

        EditText token =
                new EditText(activity);

        token.setHint(
                "RGDS_API_KEY"
        );

        token.setSingleLine(true);

        token.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        /*
         * Si ya se escribió anteriormente,
         * permanece almacenada en la app.
         */
        token.setText(
                prefs.getString("token", "")
        );

        form.addView(token);

        TextView note =
                new TextView(activity);

        note.setPadding(
                0,
                16,
                0,
                0
        );

        note.setText(
                "RGDS Dashboard enviará un extracto del diagnóstico "
                        + "al abrir la app, aproximadamente cada 5 minutos "
                        + "y cuando Dashboard deje de estar visible.\n\n"
                        + "El LOG completo permanece en la consola. "
                        + "Cloudflare Observability no sustituye al LOG local "
                        + "ni envía correo.\n\n"
                        + "Estado actual: "
                        + state
        );

        form.addView(note);

        ScrollView scroll =
                new ScrollView(activity);

        scroll.addView(form);

        new AlertDialog.Builder(activity)
                .setTitle("Diagnósticos automáticos")
                .setView(scroll)

                .setPositiveButton(
                        "Guardar",
                        (dialog, which) -> {

                            String secret =
                                    token.getText()
                                            .toString()
                                            .trim();

                            if (secret.contains("\r")
                                    || secret.contains("\n")) {

                                Toast.makeText(
                                        activity,
                                        "La API key no es válida",
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }

                            /*
                             * El Worker exige mínimo 32 caracteres.
                             */
                            if (enabled.isChecked()
                                    && secret.length() < 32) {

                                Toast.makeText(
                                        activity,
                                        "RGDS_API_KEY debe tener al menos 32 caracteres",
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }

                            prefs.edit()
                                    .putBoolean(
                                            "enabled",
                                            enabled.isChecked()
                                    )
                                    .putString(
                                            "token",
                                            secret
                                    )
                                    /*
                                     * Elimina configuración antigua
                                     * de v0.4.
                                     */
                                    .remove("endpoint")
                                    .apply();

                            if (enabled.isChecked()) {
                                state =
                                        "Preparando primer envío…";

                                worker.execute(
                                        () -> captureAndSend(true)
                                );
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
