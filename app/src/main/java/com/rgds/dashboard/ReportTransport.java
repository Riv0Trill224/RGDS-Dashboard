package com.rgds.dashboard;

final class ReportTransport {
    static final String CLOUDFLARE = "https://rgds-logger.riv0trill224.workers.dev/log";
    static boolean isCloudflare(String endpoint) { return CLOUDFLARE.equals(endpoint); }
    static String excerpt(String log) {
        int start = Math.max(0, log.length() - 16000);
        if (start > 0 && Character.isLowSurrogate(log.charAt(start))) start++;
        return log.substring(start);
    }
    static boolean accepted(boolean cloudflare, String expected, String actual, String status, boolean authenticated) {
        return expected.equals(actual) && (cloudflare
                ? authenticated && "worker_received".equals(status) : "smtp_accepted".equals(status));
    }
}
