package com.rgds.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Package matches rank first; names alone never prove ownership or display. */
final class SurfaceCatalog {
    final List<String> layers = new ArrayList<>();
    int matches, rejected, own;
    String error;
    SurfaceCatalog(CommandResult result, String target, String dashboard) {
        if (!result.succeeded()) {
            error = "Error consultando SurfaceFlinger: exit=" + result.exitCode
                    + (result.timedOut ? " (timeout)" : "");
            return;
        }
        if (result.truncated) { error = "Lista de superficies incompleta (truncada)"; return; }
        if (result.stdout.contains("Permission Denial") || result.stdout.contains("Permission denied")) {
            error = "SurfaceFlinger denegó la consulta"; return;
        }
        Set<String> preferred = new LinkedHashSet<>(), others = new LinkedHashSet<>();
        Pattern game = packagePattern(target), self = packagePattern(dashboard);
        for (String line : result.stdout.split("\n")) {
            String layer = line.trim();
            if (layer.isEmpty()) continue;
            if (self.matcher(layer).find()) { own++; continue; }
            if (SurfaceFps.command(layer) == null) { rejected++; continue; }
            if (game.matcher(layer).find()) preferred.add(layer); else others.add(layer);
        }
        matches = preferred.size();
        layers.addAll(preferred);
        layers.addAll(others);
    }
    private static Pattern packagePattern(String value) {
        return Pattern.compile("(?<![\\w.])" + Pattern.quote(value) + "(?![\\w.])");
    }
    String summary() {
        if (error != null) return error;
        return "Superficies disponibles: " + layers.size() + "; del paquete: " + matches
                + "; Dashboard: " + own + "; nombres descartados: " + rejected
                + (layers.isEmpty() ? "\nNo se obtuvo una superficie seleccionable." : "");
    }
}
