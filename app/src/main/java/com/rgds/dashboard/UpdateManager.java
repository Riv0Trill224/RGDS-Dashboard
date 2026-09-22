package com.rgds.dashboard;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;

/** Public GitHub test channel; never uses root to bypass the Android installer. */
final class UpdateManager {
    private final Activity activity;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final SharedPreferences prefs;
    private volatile boolean closed, busy;
    private File pendingInstall;
    private JSONObject pendingMetadata;
    UpdateManager(Activity activity) {
        this.activity = activity;
        prefs = activity.getSharedPreferences("updates", 0);
    }
    private void ui(Runnable action) {
        activity.runOnUiThread(() -> { if (!closed && !activity.isFinishing()) action.run(); });
    }
    private void message(String text) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
    synchronized void check(boolean manual) {
        if (busy || closed) return;
        if (!manual && System.currentTimeMillis() - prefs.getLong("lastCheck", 0) < 6 * 3600000L) return;
        busy = true;
        if (manual) message("Buscando actualizaciones en GitHub…");
        worker.execute(() -> {
            try {
                JSONArray releases = new JSONArray(readText("https://api.github.com/repos/" + BuildConfig.UPDATE_REPOSITORY
                        + "/releases?per_page=30", 2 * 1024 * 1024));
                JSONObject best = null;
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.getJSONObject(i);
                    if (release.optBoolean("draft") || release.isNull("published_at")) continue;
                    JSONArray assets = release.getJSONArray("assets");
                    String metadataUrl = null, apkUrl = null;
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject asset = assets.getJSONObject(j);
                        if ("update.json".equals(asset.optString("name"))) metadataUrl = asset.optString("browser_download_url");
                        if ("RGDS-Dashboard.apk".equals(asset.optString("name"))) apkUrl = asset.optString("browser_download_url");
                    }
                    if (metadataUrl == null || apkUrl == null || !UpdatePolicy.officialAsset(metadataUrl, BuildConfig.UPDATE_REPOSITORY)
                            || !UpdatePolicy.officialAsset(apkUrl, BuildConfig.UPDATE_REPOSITORY)) continue;
                    JSONObject info = new JSONObject(readText(metadataUrl, 16384));
                    validateMetadata(info);
                    if (!apkUrl.equals(info.getString("url"))) continue;
                    if (info.getLong("versionCode") > BuildConfig.VERSION_CODE
                            && (best == null || info.getLong("versionCode") > best.getLong("versionCode"))) best = info;
                    // Releases are newest first; cap metadata requests to keep opening lightweight.
                    if (i >= 4) break;
                }
                prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply();
                JSONObject result = best;
                ui(() -> {
                    if (result == null) { if (manual) message("No hay una versión más nueva en el canal público."); }
                    else new AlertDialog.Builder(activity).setTitle("Actualización " + result.optString("versionName"))
                            .setMessage("Versión pública de prueba. Se verificará la descarga y Android pedirá confirmar la instalación.")
                            .setPositiveButton("Descargar", (d, w) -> download(result)).setNegativeButton("Después", null).show();
                });
            } catch (Exception e) {
                ui(() -> { if (manual) message("No se pudo consultar GitHub: " + e.getClass().getSimpleName() + ". Reintenta con conexión."); });
            } finally { busy = false; }
        });
    }
    private void validateMetadata(JSONObject info) throws Exception {
        if (info.getInt("schema") != 1 || !activity.getPackageName().equals(info.getString("packageName"))
                || !info.getString("sha256").matches("[0-9a-f]{64}") || info.getLong("size") <= 0
                || info.getLong("size") > 100 * 1024 * 1024 || info.getLong("versionCode") <= 0
                || !UpdatePolicy.officialAsset(info.getString("url"), BuildConfig.UPDATE_REPOSITORY))
            throw new IOException("Metadatos no válidos");
    }
    private synchronized void download(JSONObject info) {
        if (busy || closed) return;
        busy = true;
        message("Descargando APK; mantén Dashboard abierto…");
        worker.execute(() -> {
            File apk = SharedFiles.create(activity, ".apk");
            try {
                validateMetadata(info);
                try (OutputStream out = new FileOutputStream(apk)) {
                    transfer(info.getString("url"), out, info.getLong("size"));
                }
                verifyApk(apk, info);
                pendingInstall = apk; pendingMetadata = info;
                ui(this::install);
            } catch (Exception e) {
                apk.delete();
                ui(() -> message("Actualización rechazada o incompleta: " + e.getMessage()));
            } finally { busy = false; }
        });
    }
    @SuppressWarnings("deprecation")
    private void verifyApk(File apk, JSONObject info) throws Exception {
        if (apk.length() != info.getLong("size")) throw new IOException("tamaño incorrecto");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(apk)) {
            byte[] buffer = new byte[16384]; int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        if (!UpdatePolicy.hex(digest.digest()).equals(info.getString("sha256"))) throw new IOException("hash incorrecto");
        PackageManager pm = activity.getPackageManager();
        PackageInfo target = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        PackageInfo installed = pm.getPackageInfo(activity.getPackageName(), PackageManager.GET_SIGNATURES);
        if (target == null || !installed.packageName.equals(target.packageName)) throw new IOException("paquete incorrecto");
        long targetCode = Build.VERSION.SDK_INT >= 28 ? target.getLongVersionCode() : target.versionCode;
        long installedCode = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
        if (targetCode != info.getLong("versionCode") || targetCode <= installedCode) throw new IOException("versión incorrecta");
        if (target.signatures == null || installed.signatures == null || target.signatures.length != 1
                || installed.signatures.length != 1 || !UpdatePolicy.sameSignature(installed.signatures[0].toByteArray(),
                        target.signatures[0].toByteArray())) throw new IOException("firma incompatible");
    }
    void resumeInstall() {
        if (pendingInstall != null && activity.getPackageManager().canRequestPackageInstalls()) install();
    }
    private void install() {
        if (pendingInstall == null || closed) return;
        try {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity).setTitle("Permitir actualización")
                        .setMessage("Autoriza a Dashboard a instalar apps. Al volver se abrirá el instalador.")
                        .setPositiveButton("Abrir ajustes", (d, w) -> {
                            try { activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName()))); }
                            catch (RuntimeException e) { message("Abre manualmente los ajustes de instalar apps desconocidas."); }
                        }).setNegativeButton("Después", null).show(); return;
            }
            verifyApk(pendingInstall, pendingMetadata);
            Uri uri = SharedFiles.uri(activity, pendingInstall);
            Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("APK", uri));
            activity.startActivity(intent);
            pendingInstall = null; pendingMetadata = null;
        } catch (Exception e) { message("No se pudo iniciar el instalador: " + e.getMessage()); }
    }
    private String readText(String url, long limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transfer(url, out, limit);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
    private void transfer(String address, OutputStream out, long limit) throws Exception {
        for (int redirect = 0; redirect < 6; redirect++) {
            if (closed || Thread.currentThread().isInterrupted()) throw new IOException("cancelada");
            if (!UpdatePolicy.downloadHost(address)) throw new IOException("servidor no permitido");
            HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "RGDS-Dashboard/" + BuildConfig.VERSION_NAME);
            try {
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("redirección inválida");
                    address = new URL(new URL(address), location).toString(); continue;
                }
                if (code != 200) throw new IOException("HTTP " + code);
                if (connection.getContentLengthLong() > limit) throw new IOException("archivo demasiado grande");
                try (InputStream in = connection.getInputStream()) {
                    byte[] buffer = new byte[16384]; int count; long total = 0;
                    while ((count = in.read(buffer)) != -1) {
                        if (closed || Thread.currentThread().isInterrupted()) throw new IOException("cancelada");
                        total += count; if (total > limit) throw new IOException("límite de descarga");
                        out.write(buffer, 0, count);
                    }
                }
                return;
            } finally { connection.disconnect(); }
        }
        throw new IOException("demasiadas redirecciones");
    }
    void close() { closed = true; worker.shutdownNow(); }
}
