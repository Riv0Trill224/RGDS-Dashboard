package com.rgds.dashboard;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative interpretation. Raw evidence remains available, including unsupported formats. */
final class DiagnosticAnalysis {
    private static final Pattern MINECRAFT = Pattern.compile("(?<![\\w.])com\\.mojang\\.minecraftpe(?![\\w.])");
    private static final Pattern PID = Pattern.compile("\\bpid\\s*[=:]\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROCESS = Pattern.compile("\\b(\\d+):com\\.mojang\\.minecraftpe(?:/|\\s|})");
    private static final Pattern DISPLAY = Pattern.compile("\\b(?:mDisplayId|displayId)\\s*[=:]\\s*(\\d+)\\b");

    private DiagnosticAnalysis() { }
    static String thermal(String raw) {
        StringBuilder text = new StringBuilder();
        for (String line : raw.split("\\r?\\n")) {
            text.append(line).append('\n');
            if (line.startsWith("temp:"))
                text.append("interpretado: ").append(temperature(line.substring(5).trim())).append('\n');
        }
        return text.toString();
    }
    static String temperature(String raw) {
        if (!raw.matches("[+-]?\\d+")) return "-- (formato no reconocido; conservar RAW)";
        try {
            long value = Long.parseLong(raw);
            if (value < -40000 || value > 150000 || (value != 0 && Math.abs(value) < 1000))
                return "-- (valor/unidad ambiguos o fuera de rango; conservar RAW)";
            return String.format(Locale.getDefault(), "%.1f °C (sysfs: miligrados)", value / 1000d);
        } catch (NumberFormatException e) { return "-- (valor fuera de rango; conservar RAW)"; }
    }
    static String minecraft(Map<String, CommandResult> results) {
        Set<String> pids = new LinkedHashSet<>(), displays = new LinkedHashSet<>(), surfaces = new LinkedHashSet<>();
        StringBuilder evidence = new StringBuilder();
        boolean found = false, complete = true;
        int lines = 0;
        for (String key : new String[]{"SURFACEFLINGER", "ACTIVITY DUMPSYS", "WINDOW DUMPSYS"}) {
            CommandResult result = results.get(key);
            if (result == null) { complete = false; continue; }
            if (!result.succeeded() || result.truncated || result.stdout.trim().isEmpty()
                    || result.stdout.contains("Permission Denial") || result.stdout.contains("Permission denied")) complete = false;
            for (String line : result.stdout.split("\\r?\\n")) {
                if (!MINECRAFT.matcher(line).find()) continue;
                found = true;
                // IDs must be explicit on the SAME line as the package, never borrowed from nearby windows.
                if (!"SURFACEFLINGER".equals(key)) {
                    addMatches(PID, line, pids);
                    addMatches(PROCESS, line, pids);
                    addMatches(DISPLAY, line, displays);
                } else if (!line.contains("Permission") && !line.contains("Error")) {
                    surfaces.add(line.trim());
                }
                if (++lines <= 16) evidence.append('[').append(key).append("] ").append(line.trim()).append('\n');
            }
        }
        return "Minecraft detectado: " + (found ? "SÍ (mención en las salidas)" : "NO (sin coincidencias)")
                + (complete ? "" : "\nCobertura parcial: no permite descartar que esté ejecutándose.")
                + "\nPID: " + joined(pids) + "\nDisplay: " + joined(displays)
                + "\nSurface: " + joined(surfaces) + "\n"
                + "No se deduce un display a partir del foco ni se mide FPS.\n" + evidence
                + (lines > 16 ? "[Más coincidencias en RAW]\n" : "");
    }
    private static void addMatches(Pattern pattern, String line, Set<String> values) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) values.add(matcher.group(1));
    }
    private static String joined(Set<String> values) { return values.isEmpty() ? "--" : String.join(", ", values); }
}
