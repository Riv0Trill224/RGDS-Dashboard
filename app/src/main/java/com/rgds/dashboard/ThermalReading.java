package com.rgds.dashboard;

/** Parses the fixed read-only thermal probe; preserves the actual sensor name. */
final class ThermalReading {
    Float celsius;
    String source = "Sin zonas con temperatura válida";
    static ThermalReading parse(String output) {
        ThermalReading result = new ThermalReading();
        String zone = "", type = "";
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.startsWith("/sys/class/thermal/thermal_zone")) {
                zone = line.substring(line.lastIndexOf('/') + 1);
                type = zone;
            } else if (line.startsWith("type: ")) {
                type = line.substring(6).trim();
            } else if (!zone.isEmpty() && line.startsWith("temp: ")) {
                try {
                    float value = Float.parseFloat(line.substring(6).trim()) / 1000f;
                    if (Float.isFinite(value) && value >= -40 && value <= 150
                            && (result.celsius == null || value > result.celsius)) {
                        result.celsius = value;
                        result.source = type.isEmpty() ? zone : type;
                    }
                } catch (NumberFormatException ignored) { }
            }
        }
        return result;
    }
}
