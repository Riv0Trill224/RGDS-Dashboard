package com.rgds.dashboard;

import java.util.LinkedHashMap;
import java.util.Map;

/** Closed read-only catalog. No UI, file contents or device names become shell code. */
final class DiagnosticCommands {
    private DiagnosticCommands() { }
    static final String THERMAL =
            "errors=0; count=0; for node in /sys/class/thermal/thermal_zone*; do "
            + "[ -d \"$node\" ] || continue; count=$((count+1)); "
            + "[ \"$count\" -le 128 ] || { printf '[LIMITE: 128 zonas]\\n'; break; }; "
            + "printf '\\n%s\\n' \"$node\"; "
            + "for field in type temp; do printf '%s: ' \"$field\"; "
            + "cat \"$node/$field\" || errors=1; printf '\\n'; done; done; "
            + "[ \"$count\" -gt 0 ] || printf '[Sin zonas visibles o sin permiso de enumeración]\\n'; exit \"$errors\"";
    static final String CPU_FREQ =
            "errors=0; count=0; for node in /sys/devices/system/cpu/cpu[0-9]*/cpufreq; do "
            + "[ -d \"$node\" ] || continue; count=$((count+1)); "
            + "[ \"$count\" -le 128 ] || { printf '[LIMITE: 128 CPUs]\\n'; break; }; "
            + "printf '\\n%s\\n' \"$node\"; "
            + "for field in scaling_cur_freq scaling_min_freq scaling_max_freq cpuinfo_cur_freq cpuinfo_max_freq; do "
            + "printf '%s (kHz RAW): ' \"$field\"; cat \"$node/$field\" || errors=1; printf '\\n'; done; done; "
            + "[ \"$count\" -gt 0 ] || printf '[Sin cpufreq visible o sin permiso de enumeración]\\n'; exit \"$errors\"";
    static final String DEVFREQ =
            "errors=0; count=0; for node in /sys/class/devfreq/*; do "
            + "[ -d \"$node\" ] || continue; count=$((count+1)); "
            + "[ \"$count\" -le 128 ] || { printf '[LIMITE: 128 nodos]\\n'; break; }; "
            + "printf '\\n%s\\n' \"$node\"; "
            + "for field in name cur_freq min_freq max_freq available_frequencies load utilization; do "
            + "if [ -e \"$node/$field\" ]; then printf '%s RAW: ' \"$field\"; "
            + "cat \"$node/$field\" || errors=1; printf '\\n'; fi; done; done; "
            + "[ \"$count\" -gt 0 ] || printf '[Sin devfreq visible o sin permiso de enumeración]\\n'; exit \"$errors\"";
    // Three explicit levels, at most 256 visited directories and 32 matches; no recursive find.
    static final String GPU_SCAN =
            "errors=0; visited=0; found=0; "
            + "scan() { for node in \"$1\"/*; do [ -d \"$node\" ] || continue; "
            + "[ \"$visited\" -lt 256 ] || return; visited=$((visited+1)); "
            + "case \"$node\" in *[Gg][Pp][Uu]*|*[Mm][Aa][Ll][Ii]*) "
            + "[ \"$found\" -lt 32 ] || return; found=$((found+1)); "
            + "printf '\\n%s\\n' \"$node\"; "
            + "for field in name cur_freq min_freq max_freq available_frequencies load utilization; do "
            + "if [ -f \"$node/$field\" ]; then printf '%s RAW: ' \"$field\"; "
            + "cat \"$node/$field\" || errors=1; printf '\\n'; fi; done;; esac; "
            + "if [ \"$2\" -lt 3 ] && [ ! -L \"$node\" ]; then scan \"$node\" \"$(( $2 + 1 ))\"; fi; "
            + "[ \"$visited\" -lt 256 ] && [ \"$found\" -lt 32 ] || return; done; }; "
            + "scan /sys/devices/platform 1; "
            + "printf '\\nBusqueda limitada: profundidad 3; visitados=%s; coincidencias=%s (max 32)\\n' \"$visited\" \"$found\"; exit \"$errors\"";

    static Map<String, String> catalog() {
        Map<String, String> commands = new LinkedHashMap<>();
        commands.put("ROOT", "id");
        commands.put("KERNEL", "uname -a");
        commands.put("CPUINFO", "cat /proc/cpuinfo");
        commands.put("MEMINFO", "cat /proc/meminfo");
        commands.put("SURFACEFLINGER", "dumpsys SurfaceFlinger --list");
        commands.put("DISPLAY DUMPSYS", "dumpsys display");
        commands.put("ACTIVITY DUMPSYS", "dumpsys activity activities");
        commands.put("WINDOW DUMPSYS", "dumpsys window windows");
        commands.put("THERMAL", THERMAL);
        commands.put("CPU FREQUENCIES", CPU_FREQ);
        commands.put("DEVFREQ", DEVFREQ);
        commands.put("GPU PLATFORM SEARCH", GPU_SCAN);
        return commands;
    }
    static boolean isAllowed(String command) { return catalog().containsValue(command); }
}
