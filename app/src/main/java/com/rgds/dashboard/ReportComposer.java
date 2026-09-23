package com.rgds.dashboard;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;

final class ReportComposer {
    private final Activity activity;
    private final SharedPreferences prefs;
    ReportComposer(Activity activity) {
        this.activity = activity; prefs = activity.getSharedPreferences("dashboard", 0);
    }
    void configure() {
        EditText address = new EditText(activity);
        address.setSingleLine(true); address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        address.setText(prefs.getString("reportEmail", "riv0trill224@icloud.com"));
        new AlertDialog.Builder(activity).setTitle("Correo dedicado para reportes")
                .setMessage("Dirección de destino. No se necesita contraseña. Cada envío se confirma en tu cliente de correo.")
                .setView(address).setPositiveButton("Guardar", (d, w) -> {
                    String value = address.getText().toString().trim();
                    if (!value.isEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()) { message("Dirección no válida"); return; }
                    prefs.edit().putString("reportEmail", value).apply();
                }).setNegativeButton("Cancelar", null).show();
    }
    void chooseSaved() {
        File[] logs = new File(activity.getFilesDir(), "sessions").listFiles((dir, name) -> name.endsWith(".log"));
        if (logs == null || logs.length == 0) { message("No hay registros guardados."); return; }
        Arrays.sort(logs, Comparator.comparingLong(File::lastModified).reversed());
        String[] names = new String[logs.length];
        for (int i = 0; i < logs.length; i++) names[i] = logs[i].getName();
        new AlertDialog.Builder(activity).setTitle("Sesiones anteriores / interrumpidas")
                .setItems(names, (d, which) -> {
                    try { preview(Files.readAllBytes(logs[which].toPath()), names[which]); }
                    catch (IOException e) { message("No se pudo leer el registro."); }
                }).show();
    }
    void preview(byte[] log, String name) {
        LinearLayout form = new LinearLayout(activity); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(20, 8, 20, 8);
        EditText steps = field(form, "Pasos realizados");
        EditText expected = field(form, "Qué esperabas");
        EditText actual = field(form, "Qué ocurrió / incidencia");
        TextView explanation = new TextView(activity);
        explanation.setText("Adjunto: " + name + " (" + log.length + " bytes). Incluye modelo, versiones, métricas y errores. Revisa el contenido antes de compartir. No incluye diagnóstico RAW de otras apps.");
        form.addView(explanation);
        ScrollView scroll = new ScrollView(activity); scroll.addView(form);
        new AlertDialog.Builder(activity).setTitle("Preparar reporte").setView(scroll)
                .setNeutralButton("Ver log", (d, w) -> {
                    TextView text = new TextView(activity); text.setTextIsSelectable(true);
                    String content = new String(log, StandardCharsets.UTF_8);
                    text.setText(content.length() > 60000 ? content.substring(0, 60000) + "\n[Vista parcial; exporta para leer completo]" : content);
                    ScrollView view = new ScrollView(activity); view.addView(text);
                    new AlertDialog.Builder(activity).setTitle(name).setView(view)
                            .setPositiveButton("Volver", (dialog, which) -> preview(log, name)).show();
                })
                .setPositiveButton("Abrir correo", (d, w) -> {
                    String notes = "\nREPORT schema=1\nPasos: " + steps.getText() + "\nEsperado: " + expected.getText()
                            + "\nObservado: " + actual.getText() + "\n";
                    send(log, notes, name);
                }).setNegativeButton("Cancelar", null).show();
    }
    private EditText field(LinearLayout form, String hint) {
        EditText field = new EditText(activity); field.setHint(hint);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4000)});
        form.addView(field); return field;
    }
    private void send(byte[] log, String notes, String name) {
        String address = prefs.getString("reportEmail", "riv0trill224@icloud.com");
        if (address.isEmpty()) { message("Configura primero el correo de destino en Reportes."); configure(); return; }
        try {
            File file = SharedFiles.create(activity, ".log");
            try (OutputStream out = new FileOutputStream(file)) { out.write(log); out.write(notes.getBytes(StandardCharsets.UTF_8)); }
            Uri uri = SharedFiles.uri(activity, file);
            Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_EMAIL, new String[]{address})
                    .putExtra(Intent.EXTRA_SUBJECT, "[RGDS][" + name + "][" + Build.MODEL + "] Reporte")
                    .putExtra(Intent.EXTRA_TEXT, "Reporte de RGDS Dashboard.\n" + notes)
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("Log de prueba", uri));
            activity.startActivity(Intent.createChooser(intent, "Enviar con tu cliente de correo"));
            message("Correo preparado. Confirma el envío; la app no puede verificar la entrega.");
        } catch (Exception e) { message("No se pudo abrir el correo. Exporta con LOG y envía desde el PC."); }
    }
    private void message(String text) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
}
