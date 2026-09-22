package com.rgds.dashboard;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Private bounded logs. Export is an explicit user action; no automatic network upload. */
final class SessionLog {
    private final File file;
    private boolean failed;
    private boolean full, finished;
    private final long started = System.nanoTime();
    SessionLog(File directory) { this(directory, "unknown"); }
    SessionLog(File directory, String version) {
        directory.mkdirs();
        File[] old = directory.listFiles((dir, name) -> name.endsWith(".log"));
        if (old != null) {
            Arrays.sort(old, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < old.length - 9; i++) old[i].delete();
        }
        file = new File(directory, "rgds-v" + version + "-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date()) + "-" + UUID.randomUUID().toString().substring(0, 8) + ".log");
    }
    synchronized void write(String text) {
        if (full || finished) return;
        append(text, false);
    }
    private void append(String text, boolean ending) {
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            String line = "{\"schema\":1,\"time\":\"" + java.time.Instant.now()
                    + "\",\"elapsedMs\":" + ((System.nanoTime() - started) / 1000000)
                    + ",\"event\":\"" + escape(text) + "\"}\n";
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            if (!ending && file.length() + bytes.length > 2 * 1024 * 1024 - 1024) {
                full = true;
                out.write("{\"schema\":1,\"event\":\"LOG_LIMIT_REACHED\"}\n".getBytes(StandardCharsets.UTF_8));
            } else out.write(bytes);
        } catch (IOException e) { failed = true; }
    }
    synchronized void finish(String result) {
        if (!finished) { append("END result=" + result, true); finished = true; }
    }
    synchronized boolean isFinished() { return finished; }
    private static String escape(String value) {
        StringBuilder result = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c < 32) result.append(String.format(Locale.ROOT, "\\u%04x", (int)c));
            else result.append(c);
        }
        return result.toString();
    }
    synchronized String status() {
        return failed ? "LOG: error de escritura" : full ? "LOG: límite 2 MiB"
                : finished ? "Prueba finalizada" : "LOG activo";
    }
    synchronized byte[] snapshot() throws IOException {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }
    String name() { return file.getName(); }
}
