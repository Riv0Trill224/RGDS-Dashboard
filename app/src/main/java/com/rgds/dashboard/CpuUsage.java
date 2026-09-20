package com.rgds.dashboard;

/** Aggregate system CPU, not this process. Guest time is already in user/nice. */
public final class CpuUsage {
    private long lastTotal = -1, lastIdle;
    public void reset() { lastTotal = -1; }
    public Float sample(String line) {
        try {
            String[] fields = line.trim().split("\\s+");
            if (!"cpu".equals(fields[0]) || fields.length < 5) { reset(); return null; }
            long total = 0, idle = 0;
            for (int i = 1; i < Math.min(fields.length, 9); i++) {
                long value = Long.parseLong(fields[i]);
                if (value < 0) { reset(); return null; }
                total = Math.addExact(total, value);
                if (i == 4 || i == 5) idle = Math.addExact(idle, value);
            }
            long delta = total - lastTotal, idleDelta = idle - lastIdle;
            Float result = lastTotal >= 0 && delta > 0 && idleDelta >= 0 && idleDelta <= delta
                    ? 100f * (delta - idleDelta) / delta : null;
            lastTotal = total;
            lastIdle = idle;
            return result;
        } catch (RuntimeException e) { reset(); return null; }
    }
}
