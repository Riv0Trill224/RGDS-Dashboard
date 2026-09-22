package com.rgds.dashboard;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Private bounded logs. Export is an explicit user action; no automatic network upload. */
final class SessionLog {
    private final File file;
    private boolean failed;
    SessionLog(File directory) {
        directory.mkdirs();
        File[] old = directory.listFiles((dir, name) -> name.endsWith(".log"));
        if (old != null) {
            Arrays.sort(old, Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < old.length - 9; i++) old[i].delete();
        }
        file = new File(directory, "rgds-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date()) + "-" + UUID.randomUUID().toString().substring(0, 8) + ".log");
    }
    synchronized void write(String text) {
        if (file.length() >= 2 * 1024 * 1024) return;
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            String line = System.currentTimeMillis() + " " + text + "\n";
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            int remaining = (int) Math.max(0, 2 * 1024 * 1024 - file.length());
            out.write(bytes, 0, Math.min(bytes.length, remaining));
        } catch (IOException e) { failed = true; }
    }
    synchronized String status() {
        return failed ? "LOG: error de escritura" : file.length() >= 2 * 1024 * 1024
                ? "LOG: límite 2 MiB" : "LOG activo";
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
