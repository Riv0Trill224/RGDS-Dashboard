package com.rgds.dashboard;

import java.util.TreeSet;

/** Experimental surface presentation rate, not a guarantee of game simulation FPS. */
final class SurfaceFps {
    static String command(String layer) {
        if (layer == null || layer.isEmpty() || layer.length() > 300
                || !layer.matches("[A-Za-z0-9 ._:/#@{}()\\[\\],=+\\-]+")) return null;
        return "dumpsys SurfaceFlinger --latency '" + layer + "'";
    }
    static boolean allowed(String command) {
        String prefix = "dumpsys SurfaceFlinger --latency '";
        if (!command.startsWith(prefix) || !command.endsWith("'")) return false;
        String layer = command.substring(prefix.length(), command.length() - 1);
        return command.equals(command(layer));
    }
    static FpsProvider.Reading parse(String raw, long now) {
        TreeSet<Long> times = new TreeSet<>();
        boolean rows = false, positive = false, pending = false;
        for (String line : raw.split("\n")) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length != 3) continue; // First line is refresh period, never FPS.
            try {
                long time = Long.parseLong(fields[1]);
                rows = true;
                if (time == Long.MAX_VALUE) pending = true;
                else if (time > 0) positive = true;
                if (time > 0 && time < Long.MAX_VALUE && time <= now && now - time <= 2000000000L) times.add(time);
            } catch (NumberFormatException ignored) { }
        }
        if (times.size() < 3) return new FpsProvider.Reading(null,
                raw.trim().isEmpty() ? "SurfaceFlinger devolvió salida vacía"
                : !rows ? "Sin historial de frames; ver LOG"
                : !positive ? (pending ? "Frames pendientes de presentación" : "Historial de frames en ceros")
                : times.isEmpty() ? "Tiempos antiguos/incompatibles; ver LOG" : "Menos de 3 frames recientes");
        long delta = times.last() - times.first();
        float fps = (times.size() - 1) * 1000000000f / delta;
        if (!Float.isFinite(fps) || fps <= 0 || fps > 1000) return new FpsProvider.Reading(null, "Tiempos de superficie no válidos");
        return new FpsProvider.Reading(fps, "Superficie elegida · experimental");
    }
}
