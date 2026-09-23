package com.rgds.dashboard;

import java.util.regex.Pattern;

/** Vendor HWC layer rate, not refresh rate or a global compositor average. */
final class HwcFps {
    static FpsProvider.Reading parse(String raw, String packageName) {
        if (packageName == null || packageName.isEmpty()) return missing("Selecciona juego para FPS");
        Pattern target = Pattern.compile("(?<![\\w.])" + Pattern.quote(packageName) + "(?![\\w.])");
        boolean hwc = false;
        String display = "?", matchedDisplay = "?";
        int rateColumn = -1, nameColumn = -1, matches = 0;
        Float rate = null;
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.equals("h/w composer state:")) { hwc = true; continue; }
            if (!hwc) continue;
            if (trimmed.startsWith("DisplayId=")) {
                display = trimmed.substring(10).split("[, ]", 2)[0];
                rateColumn = nameColumn = -1;
                continue;
            }
            if (!trimmed.contains("|")) {
                if (!trimmed.isEmpty() && !trimmed.matches("[-+]+")) rateColumn = nameColumn = -1;
                continue;
            }
            String[] columns = line.split("\\|", -1);
            int newRate = -1, newName = -1;
            for (int i = 0; i < columns.length; i++) {
                if (columns[i].trim().equals("mFps")) newRate = i;
                if (columns[i].trim().equals("name")) newName = i;
            }
            if (newRate >= 0 && newName >= 0) { rateColumn = newRate; nameColumn = newName; continue; }
            if (rateColumn < 0 || nameColumn < 0 || columns.length <= Math.max(rateColumn, nameColumn)) continue;
            if (!target.matcher(columns[nameColumn]).find()) continue;
            matches++;
            matchedDisplay = display;
            try {
                float value = Float.parseFloat(columns[rateColumn].trim());
                if (Float.isFinite(value) && value >= 0 && value <= 1000) rate = value;
            } catch (NumberFormatException ignored) { }
        }
        if (matches > 1) return missing("Varias capas del juego: FPS ambiguos");
        if (matches == 0 || rate == null) return missing("Sin mFps válido del juego; ver LOG");
        return new FpsProvider.Reading(rate, "FPS compositor · HWC " + matchedDisplay);
    }
    private static FpsProvider.Reading missing(String reason) { return new FpsProvider.Reading(null, reason); }
}
