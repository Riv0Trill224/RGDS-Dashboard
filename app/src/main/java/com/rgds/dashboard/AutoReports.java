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

/** Development-only opt-in queue. Secrets are entered privately, never built into the APK. */
final class AutoReports {
    private final Context context;
    private final SharedPreferences prefs;
    private final Supplier<SessionLog> source;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final File queue;
    private volatile String state = "Autoenvío desactivado";
    private long lastSnapshot;
    AutoReports(Context context, Supplier<SessionLog> source) {
        this.context = context.getApplicationContext(); this.source = source;
        prefs = context.getSharedPreferences("private-report-relay", 0);
        queue = new File(context.getFilesDir(), "report-queue"); queue.mkdirs();
        worker.scheduleWithFixedDelay(() -> captureAndSend(false), 5, 5, TimeUnit.MINUTES);
        if (prefs.getBoolean("enabled", false)) worker.execute(() -> captureAndSend(true));
    }
    String status() { return state; }
    void checkpoint() { if (!worker.isShutdown()) worker.execute(() -> captureAndSend(false)); }
    void close() { worker.shutdown(); }
    private void captureAndSend(boolean force) {
        if (!prefs.getBoolean("enabled", false)) { state = "Autoenvío desactivado"; return; }
        try {
            // Persist before network I/O. Avoid duplicate snapshots when opening system dialogs.
            long now = android.os.SystemClock.elapsedRealtime();
            if (force || now - lastSnapshot >= 60000) {
                File[] files = queue.listFiles((d, n) -> n.endsWith(".json"));
                if (files != null && files.length >= 10) { state = "Cola llena (10); conserva LOG local"; }
                else {
                    SessionLog log = source.get();
                    String id = UUID.randomUUID().toString();
                    JSONObject body = new JSONObject().put("schema", 1).put("reportId", id)
                            .put("version", BuildConfig.VERSION_NAME).put("session", log.name())
                            .put("log", new String(log.snapshot(), StandardCharsets.UTF_8));
                    File temp = new File(queue, id + ".tmp");
                    try (FileOutputStream out = new FileOutputStream(temp)) {
                        out.write(body.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync();
                    }
                    if (!temp.renameTo(new File(queue, id + ".json"))) throw new IOException("No se pudo guardar cola");
                    lastSnapshot = now;
                }
            }
            String endpoint = prefs.getString("endpoint", ReportTransport.CLOUDFLARE), token = prefs.getString("token", "");
            if (endpoint.isEmpty() || token.isEmpty()) { state = "Guardado: falta configurar servidor HTTPS"; return; }
            URL url = new URL(endpoint);
            boolean cloudflare = ReportTransport.isCloudflare(endpoint);
            if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null) throw new IOException("Se requiere HTTPS sin credenciales en URL");
            File[] files = queue.listFiles((d, n) -> n.endsWith(".json"));
            if (files == null) return;
            Arrays.sort(files, Comparator.comparingLong(File::lastModified));
            for (File file : files) {
                if (!prefs.getBoolean("enabled", false)) return;
                byte[] data = Files.readAllBytes(file.toPath());
                JSONObject report = new JSONObject(new String(data, StandardCharsets.UTF_8));
                if (cloudflare) {
                    String original = report.getString("log");
                    String excerpt = ReportTransport.excerpt(original);
                    report.put("log", excerpt).put("logTruncated", !original.equals(excerpt))
                            .put("originalLogBytes", original.getBytes(StandardCharsets.UTF_8).length);
                    data = report.toString().getBytes(StandardCharsets.UTF_8);
                }
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false); // Never forward auth to a redirected host.
                connection.setConnectTimeout(10000); connection.setReadTimeout(20000);
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(data.length);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                try {
                    try (OutputStream out = connection.getOutputStream()) { out.write(data); }
                    if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
                    ByteArrayOutputStream response = new ByteArrayOutputStream();
                    try (InputStream in = connection.getInputStream()) {
                        byte[] buffer = new byte[1024]; int count;
                        while ((count = in.read(buffer)) != -1) {
                            if (response.size() + count > 8192) throw new IOException("Respuesta demasiado grande");
                            response.write(buffer, 0, count);
                        }
                    }
                    JSONObject ack = new JSONObject(new String(response.toByteArray(), StandardCharsets.UTF_8));
                    if (!ReportTransport.accepted(cloudflare, report.getString("reportId"), ack.optString("reportId"),
                            ack.optString("status"), ack.optBoolean("authenticated")))
                        throw new IOException(cloudflare ? "Actualiza el Worker: falta acuse autenticado" : "Sin confirmación SMTP");
                    if (!file.delete()) throw new IOException("No se pudo retirar reporte confirmado");
                    state = cloudflare ? "Cloudflare recibió extracto; revisa Observability. No es correo."
                            : "SMTP aceptó reporte; recepción no verificada";
                } finally { connection.disconnect(); }
            }
        } catch (Exception e) { state = "Pendiente; reintento en 5 min. "
                + (e instanceof IOException && e.getMessage() != null && (e.getMessage().startsWith("HTTP ")
                || e.getMessage().startsWith("Actualiza el Worker")) ? e.getMessage() : e.getClass().getSimpleName()); }
    }
    void configure(Activity activity) {
        LinearLayout form = new LinearLayout(activity); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(24, 12, 24, 12);
        Switch enabled = new Switch(activity); enabled.setText("Modo de desarrollo: captura automática cada 5 min");
        enabled.setChecked(prefs.getBoolean("enabled", false)); form.addView(enabled);
        EditText endpoint = new EditText(activity); endpoint.setHint(ReportTransport.CLOUDFLARE); endpoint.setSingleLine(true);
        endpoint.setText(prefs.getString("endpoint", ReportTransport.CLOUDFLARE)); form.addView(endpoint);
        EditText token = new EditText(activity); token.setHint("Valor privado de RGDS_API_KEY del Worker"); token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setText(prefs.getString("token", "")); form.addView(token);
        TextView note = new TextView(activity);
        note.setText("Cloudflare: extracto reciente del diagnóstico cada 5 min, sin enviar correo. Incluye modelo/ROM si aparece en el extracto, juego, métricas y errores. Conserva LOG completo en la consola. Requiere Worker actualizado y RGDS_API_KEY. " + state);
        form.addView(note);
        ScrollView scroll = new ScrollView(activity); scroll.addView(form);
        new AlertDialog.Builder(activity).setTitle("Autoenvío de desarrollo").setView(scroll)
                .setPositiveButton("Guardar", (d, w) -> {
                    String url = endpoint.getText().toString().trim(), secret = token.getText().toString().trim();
                    if (!url.isEmpty()) {
                        try { URL parsed = new URL(url); if (!"https".equals(parsed.getProtocol()) || parsed.getUserInfo() != null) throw new Exception(); }
                        catch (Exception e) { Toast.makeText(activity, "URL HTTPS no válida", Toast.LENGTH_LONG).show(); return; }
                    }
                    if (secret.contains("\r") || secret.contains("\n")) return;
                    prefs.edit().putBoolean("enabled", enabled.isChecked()).putString("endpoint", url).putString("token", secret).apply();
                    worker.execute(() -> captureAndSend(true));
                }).setNeutralButton("Estado", (d, w) -> Toast.makeText(activity, state, Toast.LENGTH_LONG).show())
                .setNegativeButton("Cancelar", null).show();
    }
}
