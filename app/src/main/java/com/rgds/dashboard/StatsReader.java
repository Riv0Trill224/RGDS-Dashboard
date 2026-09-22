package com.rgds.dashboard;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

public final class StatsReader {
    public static final class Snapshot {
        public String battery = "-- %", ram = "No disponible", cpu = "No disponible";
        public String thermal = "No disponible", batteryTemp = "No disponible";
        public String thermalSource = "Sin sensor accesible";
        public String batteryStatus = "API batería sin dato válido", ramStatus = "API memoria sin dato válido";
        public String cpuStatus = "Esperando lectura", batteryTempStatus = "Sin dato en ACTION_BATTERY_CHANGED";
        public String rootStatus = "ROOT: verificando…", logStatus = "";
        public String target = "Sin selección", targetStatus = "Selecciona aplicación y pantalla", updated = "--";
        public FpsProvider.Reading fps = new FpsProvider.Reading(null, "FPS del juego no disponible");
    }
    private final Context context;
    private final CpuUsage cpu = new CpuUsage();
    private boolean previousRoot;
    public StatsReader(Context context) { this.context = context.getApplicationContext(); }
    public void reset() { cpu.reset(); }
    public Snapshot read(RootShell shell, boolean root, SessionLog log) {
        Snapshot s = new Snapshot();
        if (root != previousRoot) { cpu.reset(); previousRoot = root; }
        try {
            BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            int percent = manager == null ? -1 : manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (percent >= 0 && percent <= 100) { s.battery = percent + " %"; s.batteryStatus = "BatteryManager"; }
        } catch (RuntimeException e) { s.batteryStatus = e.getClass().getSimpleName(); }
        try {
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null && battery.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) {
                float temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE) / 10f;
                if (temp >= -40 && temp <= 120) { s.batteryTemp = formatTemp(temp); s.batteryTempStatus = "Sensor batería (Android)"; }
            }
        } catch (RuntimeException e) { s.batteryTempStatus = e.getClass().getSimpleName(); }
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            if (manager != null) {
                manager.getMemoryInfo(info);
                if (info.totalMem > 0 && info.availMem >= 0 && info.availMem <= info.totalMem) {
                    s.ram = String.format(Locale.getDefault(), "%.2f / %.2f GiB",
                            (info.totalMem - info.availMem) / 1073741824d, info.totalMem / 1073741824d);
                    s.ramStatus = "ActivityManager · usada/total";
                }
            }
        } catch (RuntimeException e) { s.ramStatus = e.getClass().getSimpleName(); }
        try {
            String line;
            if (root) {
                CommandResult result = shell.runRootCommand("cat /proc/stat");
                if (!result.succeeded() || result.truncated) {
                    cpu.reset(); s.cpuStatus = failure(result);
                    log.write("CPU root: " + result.diagnosticText());
                    line = null;
                } else { line = result.stdout.split("\n", 2)[0]; s.cpuStatus = "Root · /proc/stat"; }
            } else { line = firstLine(new File("/proc/stat")); s.cpuStatus = "Sin root · /proc/stat"; }
            Float usage = line == null ? null : cpu.sample(line);
            if (usage != null) s.cpu = String.format(Locale.getDefault(), "%.0f %%", usage);
            else if (line != null) s.cpuStatus = "Esperando delta válido de CPU";
        } catch (Exception e) { cpu.reset(); s.cpuStatus = "Sin root: " + e.getClass().getSimpleName(); }
        if (root) {
            CommandResult result = shell.runRootCommand(DiagnosticCommands.THERMAL);
            ThermalReading reading = ThermalReading.parse(result.stdout);
            if (reading.celsius != null && !result.timedOut && !result.interrupted && !result.truncated) {
                s.thermal = formatTemp(reading.celsius);
                s.thermalSource = reading.source + (result.succeeded() ? " · root" : " · parcial");
            } else s.thermalSource = result.succeeded() ? reading.source : failure(result);
            if (!result.succeeded() || result.truncated || reading.celsius == null)
                log.write("THERMAL root: " + result.diagnosticText());
            return s;
        }
        // Report the hottest readable zone with its actual type, never label it CPU by index.
        try {
            File[] zones = new File("/sys/class/thermal").listFiles();
            Float hottest = null;
            if (zones != null) for (File zone : zones) {
                if (!zone.getName().startsWith("thermal_zone")) continue;
                try {
                    float temp = Float.parseFloat(firstLine(new File(zone, "temp"))) / 1000f;
                    String type = zone.getName();
                    try { type = firstLine(new File(zone, "type")); }
                    catch (Exception ignored) { }
                    if (temp >= -40 && temp <= 150 && (hottest == null || temp > hottest)) {
                        hottest = temp;
                        s.thermal = formatTemp(temp);
                        s.thermalSource = (type == null || type.trim().isEmpty()) ? zone.getName() : type.trim();
                    }
                } catch (Exception ignored) { }
            }
        } catch (RuntimeException e) { s.thermalSource = "Sin root: " + e.getClass().getSimpleName(); }
        return s;
    }
    static String failure(CommandResult result) {
        if (result.interrupted) return "Lectura cancelada";
        if (result.timedOut) return "Tiempo de espera agotado";
        if (result.startFailed) return "No se pudo ejecutar su";
        if (result.truncated) return "Salida incompleta";
        return "Error de lectura (exit " + result.exitCode + ") · ver LOG";
    }
    private static String firstLine(File file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) { return reader.readLine(); }
    }
    private static String formatTemp(float value) {
        return String.format(Locale.getDefault(), "%.1f °C", value);
    }
}
