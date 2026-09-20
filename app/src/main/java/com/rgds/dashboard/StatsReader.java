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
        public FpsProvider.Reading fps = new FpsProvider.Reading(null, "FPS del juego no disponible");
    }
    private final Context context;
    private final CpuUsage cpu = new CpuUsage();
    public StatsReader(Context context) { this.context = context.getApplicationContext(); }
    public void reset() { cpu.reset(); }
    public Snapshot read() {
        Snapshot s = new Snapshot();
        try {
            BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            int percent = manager == null ? -1 : manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (percent >= 0 && percent <= 100) s.battery = percent + " %";
        } catch (RuntimeException ignored) { }
        try {
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null && battery.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) {
                float temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE) / 10f;
                if (temp >= -40 && temp <= 120) s.batteryTemp = formatTemp(temp);
            }
        } catch (RuntimeException ignored) { }
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            if (manager != null) {
                manager.getMemoryInfo(info);
                if (info.totalMem > 0 && info.availMem >= 0 && info.availMem <= info.totalMem)
                    s.ram = String.format(Locale.getDefault(), "%.2f / %.2f GiB",
                            (info.totalMem - info.availMem) / 1073741824d, info.totalMem / 1073741824d);
            }
        } catch (RuntimeException ignored) { }
        try {
            Float usage = cpu.sample(firstLine(new File("/proc/stat")));
            if (usage != null) s.cpu = String.format(Locale.getDefault(), "%.0f %%", usage);
        } catch (Exception ignored) { cpu.reset(); }
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
        } catch (RuntimeException ignored) { }
        return s;
    }
    private static String firstLine(File file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) { return reader.readLine(); }
    }
    private static String formatTemp(float value) {
        return String.format(Locale.getDefault(), "%.1f °C", value);
    }
}
