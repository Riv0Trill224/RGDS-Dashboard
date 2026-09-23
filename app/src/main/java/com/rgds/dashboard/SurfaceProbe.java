package com.rgds.dashboard;

/** Fixed, read-only probes; preserves empty and partial results without inventing FPS. */
final class SurfaceProbe {
    interface Runner { CommandResult run(String command); }
    static final String[] COMMANDS = {"dumpsys SurfaceFlinger --list",
            "/system/bin/dumpsys SurfaceFlinger --list", "/system/bin/dumpsys SurfaceFlinger"};
    static final class Report {
        final String summary, raw;
        Report(String summary, String raw) { this.summary = summary; this.raw = raw; }
    }
    static Report collect(CommandResult identity, Runner runner) {
        StringBuilder raw = new StringBuilder("ROOT\n" + identity.diagnosticText());
        StringBuilder summary = new StringBuilder("Root: " + RootShell.rootStatus(identity) + "\n");
        if (!RootShell.isRootIdentity(identity))
            return new Report(summary + "Autoriza root y vuelve a ejecutar la prueba.", raw.toString());
        for (String command : COMMANDS) {
            if (Thread.currentThread().isInterrupted()) {
                summary.append("Prueba interrumpida; resultados parciales.\n"); break;
            }
            CommandResult result = runner.run(command);
            String status = command + "\nexit=" + result.exitCode + "; stdout=" + result.stdout.length()
                    + " caracteres; stderr=" + result.stderr.length() + "; timeout=" + result.timedOut
                    + "; truncado=" + result.truncated + "; interrumpido=" + result.interrupted
                    + "; inicioFallido=" + result.startFailed + "\n";
            summary.append(status).append('\n');
            raw.append("\n=== ").append(command).append(" ===\n").append(status).append(result.diagnosticText());
        }
        summary.append("Es una captura desde la app con root, no una prueba ADB ni una medición FPS.\n");
        return new Report(summary.toString(), raw.toString());
    }
}
