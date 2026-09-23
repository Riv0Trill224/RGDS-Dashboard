package com.rgds.dashboard;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** On-device alternative when USB/terminal access is unavailable. No automatic upload. */
public final class SurfaceProbeActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView text;
    private Button run, share, copy, raw;
    private String report = "", summary = "";
    private File saved;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        saved = new File(getFilesDir(), "surface-probe-last.log");
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        LinearLayout actions = new LinearLayout(this);
        add(actions, "Volver", () -> finish());
        run = add(actions, "Ejecutar", this::collect);
        share = add(actions, "Compartir", this::share);
        copy = add(actions, "Copiar resumen", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico FPS", summary));
        });
        raw = add(actions, "Ver RAW", () -> text.setText(report.length() > 60000
                ? report.substring(0, 60000) + "\n[Vista parcial; Compartir adjunta el informe guardado completo]" : report));
        page.addView(actions);
        text = new TextView(this); text.setTextSize(14); text.setPadding(16, 8, 16, 8); text.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this); scroll.addView(text);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(page);
        text.setText("Diagnóstico FPS · " + BuildConfig.VERSION_NAME
                + "\nMantén el juego abierto en la otra pantalla y pulsa Ejecutar. Puede tardar hasta un minuto."
                + "\nConsulta la lista habitual, la ruta /system/bin y el informe completo de SurfaceFlinger."
                + "\nEl informe puede incluir nombres de otras apps y ventanas. Se guarda en la consola; tú eliges compartirlo.");
        ready(false); run.setEnabled(false);
        worker.execute(() -> {
            try {
                if (saved.isFile()) {
                    String previous = new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8);
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        report = previous; summary = previous.split("\n=== RAW ===\n", 2)[0];
                        text.setText("Último informe guardado:\n" + summary); ready(true);
                    });
                }
            } catch (Exception ignored) { }
            finally { runOnUiThread(() -> { if (!isDestroyed()) run.setEnabled(true); }); }
        });
    }
    private Button add(LinearLayout row, String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setTextSize(11);
        row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        button.setOnClickListener(v -> action.run()); return button;
    }
    private void ready(boolean hasReport) { share.setEnabled(hasReport); copy.setEnabled(hasReport); raw.setEnabled(hasReport); }
    private void collect() {
        run.setEnabled(false); ready(false); text.setText("Comprobando root y recopilando SurfaceFlinger…");
        worker.execute(() -> {
            try {
                RootShell shell = new RootShell();
                SurfaceProbe.Report result = SurfaceProbe.collect(shell.checkRoot(), shell::runRootCommand);
                String header = "RGDS " + BuildConfig.VERSION_NAME + " · " + java.time.Instant.now()
                        + "\nAndroid " + android.os.Build.VERSION.RELEASE + " · " + android.os.Build.MODEL
                        + "\nObjetivo: " + getSharedPreferences("dashboard", 0).getString("targetPackage", "") + "\n";
                String shortText = header + result.summary;
                String full = shortText + "\n=== RAW ===\n" + result.raw;
                File tmp = new File(getFilesDir(), "surface-probe-last.tmp");
                Files.write(tmp.toPath(), full.getBytes(StandardCharsets.UTF_8));
                Files.move(tmp.toPath(), saved.toPath(), StandardCopyOption.REPLACE_EXISTING);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    report = full; summary = shortText; text.setText(summary); run.setEnabled(true); ready(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    text.setText("No se pudo guardar el diagnóstico: " + e.getClass().getSimpleName()); run.setEnabled(true);
                });
            }
        });
    }
    private void share() {
        try {
            File file = SharedFiles.create(this, ".log");
            Files.write(file.toPath(), report.getBytes(StandardCharsets.UTF_8));
            Uri uri = SharedFiles.uri(this, file);
            Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "RGDS diagnóstico SurfaceFlinger")
                    .putExtra(Intent.EXTRA_TEXT, summary).putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("Diagnóstico SurfaceFlinger", uri));
            startActivity(Intent.createChooser(intent, "Compartir diagnóstico"));
        } catch (Exception e) { Toast.makeText(this, "No se pudo compartir. Usa Copiar resumen o una foto.", Toast.LENGTH_LONG).show(); }
    }
    @Override public void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
