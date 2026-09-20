package com.rgds.dashboard;

/** Immutable result, including partial output when a command fails. */
public final class CommandResult {
    public final String stdout, stderr;
    public final int exitCode;
    public final boolean timedOut, interrupted, truncated, startFailed;

    public CommandResult(String stdout, String stderr, int exitCode, boolean timedOut,
                         boolean interrupted, boolean truncated, boolean startFailed) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
        this.timedOut = timedOut;
        this.interrupted = interrupted;
        this.truncated = truncated;
        this.startFailed = startFailed;
    }
    public boolean succeeded() { return exitCode == 0 && !timedOut && !interrupted && !startFailed; }
    public String diagnosticText() {
        return (succeeded() ? "OK" : "ERROR") + "\nexitCode: " + exitCode
                + "\ntimeout: " + timedOut + "\ninterrumpido: " + interrupted
                + "\nstdout:\n" + stdout + "\nstderr:\n" + stderr
                + (truncated ? "\n[TRUNCADO: límite de captura o streams sin cerrar; salida parcial]\n" : "\n");
    }
}
