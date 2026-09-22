package com.rgds.dashboard;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.widget.EditText;
import android.widget.Toast;
import java.util.*;

/** Manual selection is never presented as proof of foreground visibility. */
final class TargetSelection {
    private final Activity activity;
    final SharedPreferences prefs;
    TargetSelection(Activity activity) {
        this.activity = activity;
        prefs = activity.getSharedPreferences("dashboard", 0);
    }
    String packageName() { return prefs.getString("targetPackage", ""); }
    int displayId() { return prefs.getInt("targetDisplay", -1); }
    boolean displayAvailable() {
        DisplayManager manager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
        Display display = manager == null ? null : manager.getDisplay(displayId());
        return display != null && display.isValid()
                && display.getName().equals(prefs.getString("targetDisplayName", ""));
    }
    String summary() {
        return packageName().isEmpty() ? "Elige una aplicación" : displayAvailable()
                ? "Manual · display " + displayId() : "Pantalla pendiente/no disponible";
    }
    void chooseApp() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = activity.getPackageManager().queryIntentActivities(launcher, 0);
        apps.sort(Comparator.comparing(info -> info.loadLabel(activity.getPackageManager()).toString(), String.CASE_INSENSITIVE_ORDER));
        List<String> packages = new ArrayList<>(), labels = new ArrayList<>();
        labels.add("Introducir paquete manualmente"); packages.add("");
        for (ResolveInfo info : apps) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(activity.getPackageName()) || packages.contains(pkg)) continue;
            packages.add(pkg); labels.add(info.loadLabel(activity.getPackageManager()) + "\n" + pkg);
        }
        new AlertDialog.Builder(activity).setTitle("Juego o emulador objetivo")
                .setItems(labels.toArray(new String[0]), (d, index) -> {
                    if (index == 0) {
                        EditText input = new EditText(activity); input.setSingleLine(true); input.setText(packageName());
                        new AlertDialog.Builder(activity).setTitle("Nombre del paquete").setView(input)
                                .setPositiveButton("Guardar", (dialog, which) -> savePackage(input.getText().toString().trim()))
                                .setNegativeButton("Cancelar", null).show();
                    } else savePackage(packages.get(index));
                }).show();
    }
    private void savePackage(String name) {
        if (!name.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+") || name.equals(activity.getPackageName())) {
            message("Paquete no válido; elige el juego o emulador."); return;
        }
        prefs.edit().putString("targetPackage", name).remove("fpsLayer").apply();
        message("Objetivo seleccionado; su visibilidad aún no está verificada.");
    }
    void chooseDisplay(boolean moveDashboard) {
        DisplayManager manager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = manager == null ? new Display[0] : manager.getDisplays();
        String[] names = new String[displays.length];
        for (int i = 0; i < displays.length; i++) {
            Display display = displays[i];
            names[i] = "ID " + display.getDisplayId() + " · " + display.getName()
                    + " · " + display.getMode().getPhysicalWidth() + "×" + display.getMode().getPhysicalHeight();
        }
        new AlertDialog.Builder(activity).setTitle(moveDashboard ? "Mover Dashboard a…" : "¿Cuál es la pantalla del juego?")
                .setItems(names, (d, index) -> {
                    Display display = displays[index];
                    if (!moveDashboard) {
                        prefs.edit().putInt("targetDisplay", display.getDisplayId())
                                .putString("targetDisplayName", display.getName()).remove("fpsLayer").apply();
                    } else {
                        try {
                            Intent intent = new Intent(activity, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                            ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(display.getDisplayId());
                            activity.startActivity(intent, options.toBundle()); activity.finish();
                        } catch (RuntimeException e) { message("La ROM no permite mover la app a esa pantalla."); }
                    }
                }).setNegativeButton("Cancelar", null).show();
    }
    void launchGame() {
        if (!displayAvailable()) { message("Selecciona una pantalla disponible."); return; }
        try {
            Intent intent = activity.getPackageManager().getLaunchIntentForPackage(packageName());
            if (intent == null) { message("No se encontró una actividad de inicio. Abre el juego manualmente."); return; }
            activity.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(displayId()).toBundle());
        } catch (RuntimeException e) { message("La ROM o el juego no admiten ese destino; ábrelo manualmente."); }
    }
    private void message(String text) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show(); }
}
