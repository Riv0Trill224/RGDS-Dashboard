package com.rgds.dashboard;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Blocking API: use only on a worker. Every process is bounded, drained and closed. */
public final class RootShell {
    interface ProcessStarter { Process start(String... args) throws IOException; }
    private final ProcessStarter starter;
    private final long commandTimeoutMs, authorizationTimeoutMs;
    private final int outputLimit;
    private static final Pattern ROOT_ID = Pattern.compile("(?m)^uid=0(?:\\D|$)");

    public RootShell() { this(args -> new ProcessBuilder(args).start(), 6000, 30000, 524288); }
    RootShell(ProcessStarter starter, long commandTimeoutMs, long authorizationTimeoutMs, int outputLimit) {
        this.starter = starter;
        this.commandTimeoutMs = commandTimeoutMs;
        this.authorizationTimeoutMs = authorizationTimeoutMs;
        this.outputLimit = outputLimit;
    }
    public boolean isRootAvailable() { return isRootIdentity(checkRoot()); }
    public CommandResult checkRoot() { return execute("id", true, authorizationTimeoutMs); }
    public CommandResult runRootCommand(String command) { return execute(command, true, commandTimeoutMs); }
    public CommandResult runUnprivilegedCommand(String command) { return execute(command, false, commandTimeoutMs); }
    public static boolean isRootIdentity(CommandResult result) {
        return result.succeeded() && ROOT_ID.matcher(result.stdout).find();
    }
    public static String rootStatus(CommandResult result) {
        if (isRootIdentity(result)) return "AUTORIZADO";
        if (result.startFailed || result.exitCode == 127 || result.exitCode == 126) return "NO DISPONIBLE";
        return "NO AUTORIZADO";
    }
    private CommandResult execute(String command, boolean root, long timeoutMs) {
        if (!DiagnosticCommands.isAllowed(command))
            return new CommandResult("", "Comando fuera del catálogo de solo lectura", -1, false, false, false, false);
        Process process = null;
        Capture out = null, err = null;
        Thread outThread = null, errThread = null;
        int exit = -1;
        boolean timeout = false, interrupted = false, startFailed = false, incomplete = false;
        String failure = "";
        try {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            process = starter.start(root ? "su" : "sh", "-c", command);
            close(process.getOutputStream()); // Noninteractive, allow normal Magisk authorization UI.
            out = new Capture(process.getInputStream(), outputLimit);
            err = new Capture(process.getErrorStream(), outputLimit);
            outThread = drain(out, "rgds-stdout");
            errThread = drain(err, "rgds-stderr");
            if (process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) exit = process.exitValue();
            else { timeout = true; failure = "Timeout tras " + timeoutMs + " ms"; }
            if (!timeout) {
                outThread.join(300);
                errThread.join(300);
                incomplete = outThread.isAlive() || errThread.isAlive();
            }
        } catch (InterruptedException e) {
            interrupted = true;
            failure = "Recopilación interrumpida";
        } catch (IOException | SecurityException e) {
            startFailed = process == null;
            failure = e.getClass().getSimpleName() + ": " + e.getMessage();
        } catch (RuntimeException e) {
            failure = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (process != null) {
                // Only our own diagnostic subprocess is terminated, never a game/system process.
                try { if (process.isAlive()) process.destroyForcibly(); } catch (RuntimeException ignored) { }
                close(process.getInputStream());
                close(process.getErrorStream());
                close(process.getOutputStream());
            }
            if (outThread != null) outThread.interrupt();
            if (errThread != null) errThread.interrupt();
            if (interrupted) Thread.currentThread().interrupt();
        }
        return new CommandResult(out == null ? "" : out.text(),
                (err == null ? "" : err.text()) + (failure.isEmpty() ? "" : "\n" + failure), exit,
                timeout, interrupted, incomplete || (out != null && out.truncated) || (err != null && err.truncated), startFailed);
    }
    private static Thread drain(Capture capture, String name) {
        Thread thread = new Thread(capture, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
    private static void close(Closeable stream) {
        try { stream.close(); } catch (IOException | RuntimeException ignored) { }
    }
    private static final class Capture implements Runnable {
        private final InputStream input;
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private volatile boolean truncated;
        private String error = "";
        Capture(InputStream input, int limit) { this.input = input; this.limit = limit; }
        @Override public void run() {
            byte[] chunk = new byte[4096];
            try (InputStream stream = input) {
                int count;
                while ((count = stream.read(chunk)) != -1) {
                    synchronized (this) {
                        int keep = Math.min(count, limit - bytes.size());
                        bytes.write(chunk, 0, keep);
                        if (keep < count) truncated = true;
                    } // Continue draining after cap: no pipe deadlocks.
                }
            } catch (IOException | RuntimeException e) {
                synchronized (this) { error = "\n[Error leyendo stream: " + e.getClass().getSimpleName() + "]"; }
            }
        }
        synchronized String text() { return new String(bytes.toByteArray(), StandardCharsets.UTF_8) + error; }
    }
}
