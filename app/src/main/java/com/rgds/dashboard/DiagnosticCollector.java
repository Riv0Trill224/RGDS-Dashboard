package com.rgds.dashboard;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One snapshot on a worker, not a polling service. No data is sent or stored externally. */
public final class DiagnosticCollector {
    public interface Progress { void update(String section); }
    public static final class Report {
        public final String summary, raw;
        Report(String summary, String raw) { this.summary = summary; this.raw = raw; }
    }
    private final Context context;
    private final RootShell shell;
    public DiagnosticCollector(Context context) {
        this.context = context.getApplicationContext();
        shell = new RootShell();
    }
    public Report collect(boolean requestRoot, int currentDisplay, Progress progress) {
        String device = device();
        String displays = displays(currentDisplay);
        String activities = activities();
        StringBuilder raw = new StringBuilder("RGDS Dashboard " + BuildConfig.VERSION_NAME + " · ").append(new Date()).append('\n');
        raw.append("Instantánea secuencial; puede cambiar el estado entre comandos.\n")
                .append("Solo lectura. Máximo 512 KiB por stream/comando; cualquier límite se indica.\n");
        section(raw, "DEVICE", device);
        section(raw, "DISPLAYS", displays);
        section(raw, "ACTIVITYMANAGER API", activities);
        progress.update(requestRoot ? "Esperando autorización Magisk (máximo 30 s)..." : "Información sin root");
        CommandResult identity = requestRoot ? shell.checkRoot() : shell.runUnprivilegedCommand("id");
        boolean root = requestRoot && RootShell.isRootIdentity(identity);
        String rootState = requestRoot ? RootShell.rootStatus(identity) : "NO SOLICITADO (modo sin root)";
        section(raw, "ROOT", "Magisk/root: " + rootState + "\ncomando: "
                + (requestRoot ? "su -c id" : "sh -c id") + "\n" + identity.diagnosticText());
        Map<String, CommandResult> results = new LinkedHashMap<>();
        // A denied su check is never retried per command: collect the remaining data as the app UID.
        for (Map.Entry<String, String> entry : DiagnosticCommands.catalog().entrySet()) {
            if ("ROOT".equals(entry.getKey())) continue;
            if (Thread.currentThread().isInterrupted()) {
                section(raw, "CANCELADO", "Recopilación interrumpida; instantánea parcial.");
                break;
            }
            progress.update(entry.getKey() + (root ? " · root" : " · sin root"));
            CommandResult result = root ? shell.runRootCommand(entry.getValue()) : shell.runUnprivilegedCommand(entry.getValue());
            results.put(entry.getKey(), result);
            section(raw, entry.getKey(), "modo: " + (root ? "su" : "sh") + "\ncomando: " + entry.getValue()
                    + "\n" + result.diagnosticText());
        }
        CommandResult thermal = results.get("THERMAL");
        String temperatures = thermal == null ? "No disponible" : DiagnosticAnalysis.thermal(thermal.stdout)
                + (thermal.stderr.isEmpty() ? "" : "\nstderr:\n" + thermal.stderr);
        section(raw, "THERMAL INTERPRETADO", temperatures);
        String targetPackage = context.getSharedPreferences("dashboard", 0).getString("targetPackage", "");
        String target = DiagnosticAnalysis.application(results, targetPackage);
        section(raw, "APLICACIÓN OBJETIVO", target);
        StringBuilder summary = new StringBuilder();
        section(summary, "ROOT", "Magisk/root: " + rootState + "\n" + excerpt(identity.diagnosticText(), 1000));
        section(summary, "DISPOSITIVO", device + "\nKernel: " + preview(results.get("KERNEL"), 600));
        section(summary, "PANTALLAS", displays);
        section(summary, "CPU", preview(results.get("CPUINFO"), 1800)
                + "\nFRECUENCIAS (kHz RAW)\n" + preview(results.get("CPU FREQUENCIES"), 2500));
        section(summary, "MEMORIA", preview(results.get("MEMINFO"), 500));
        section(summary, "GPU / DEVFREQ", preview(results.get("DEVFREQ"), 2400)
                + "\nBÚSQUEDA GPU/MALI LIMITADA\n" + preview(results.get("GPU PLATFORM SEARCH"), 1600));
        section(summary, "TEMPERATURAS", excerpt(temperatures, 5000));
        section(summary, "APLICACIONES / DISPLAYS", activities + "\nACTIVITY\n"
                + preview(results.get("ACTIVITY DUMPSYS"), 1000) + "\nWINDOW\n"
                + preview(results.get("WINDOW DUMPSYS"), 1000) + "\nSalidas sin filtrar en RAW.");
        section(summary, "SURFACEFLINGER", preview(results.get("SURFACEFLINGER"), 3000));
        section(summary, "APLICACIÓN OBJETIVO", excerpt(target, 4000));
        return new Report(summary.toString(), raw.toString());
    }
    private static String device() {
        String kernel = "--";
        try { kernel = System.getProperty("os.version", "--"); }
        catch (RuntimeException ignored) { }
        return "Fabricante: " + Build.MANUFACTURER + "\nModelo: " + Build.MODEL
                + "\nDevice: " + Build.DEVICE + "\nBoard: " + Build.BOARD + "\nHardware: " + Build.HARDWARE
                + "\nAndroid: " + Build.VERSION.RELEASE + "\nSDK: " + Build.VERSION.SDK_INT
                + "\nBuild.DISPLAY: " + Build.DISPLAY + "\nKernel (API): " + kernel;
    }
    private String displays(int current) {
        StringBuilder text = new StringBuilder("Display de DiagnosticActivity al recopilar: ")
                .append(current < 0 ? "-- (ventana no asociada)" : current).append('\n');
        try {
            DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (manager == null) return text + "DisplayManager no disponible";
            Display[] all = manager.getDisplays();
            text.append("Displays disponibles para esta app: ").append(all.length).append('\n');
            for (Display display : all) {
                try {
                    Display.Mode mode = display.getMode();
                    Point size = new Point();
                    display.getRealSize(size);
                    text.append("\nDisplay ID: ").append(display.getDisplayId())
                            .append(display.getDisplayId() == current ? " [ACTUAL]" : "")
                            .append("\nNombre: ").append(display.getName())
                            .append("\nResolución (modo): ").append(mode.getPhysicalWidth()).append('×').append(mode.getPhysicalHeight())
                            .append("\nTamaño reportado: ").append(size.x).append('×').append(size.y)
                            .append("\nRefresh rate: ").append(display.getRefreshRate()).append(" Hz (NO FPS)")
                            .append("\nState: ").append(display.getState())
                            .append("\nFlags: 0x").append(Integer.toHexString(display.getFlags())).append('\n');
                } catch (RuntimeException e) { text.append("ERROR display: ").append(e).append('\n'); }
            }
        } catch (RuntimeException e) { text.append("ERROR DisplayManager: ").append(e); }
        return text.toString();
    }
    private String activities() {
        StringBuilder text = new StringBuilder("APIs restringidas al alcance de la app; no identifican todos los juegos de otros displays.\n");
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return text + "ActivityManager no disponible";
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(memory);
            text.append("RAM total bytes: ").append(memory.totalMem).append("\nRAM disponible bytes: ").append(memory.availMem).append('\n');
            List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
            if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes)
                text.append("Proceso visible por API: ").append(process.processName).append(" pid=").append(process.pid).append('\n');
            for (ActivityManager.AppTask task : manager.getAppTasks()) {
                try { text.append("Tarea propia: ").append(task.getTaskInfo()).append('\n'); }
                catch (RuntimeException e) { text.append("ERROR tarea: ").append(e).append('\n'); }
            }
        } catch (RuntimeException e) { text.append("ERROR ActivityManager: ").append(e); }
        return text.toString();
    }
    private static void section(StringBuilder builder, String name, String value) {
        builder.append("\n===== ").append(name).append(" =====\n").append(value).append('\n');
    }
    private static String preview(CommandResult result, int limit) {
        return result == null ? "No recopilado" : excerpt(result.diagnosticText(), limit);
    }
    private static String excerpt(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit) + "\n[Resumen recortado; ver RAW]\n";
    }
}
