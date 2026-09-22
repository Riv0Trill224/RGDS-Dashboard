package com.rgds.dashboard;

import java.net.URI;
import java.security.MessageDigest;
import java.util.Locale;

final class UpdatePolicy {
    static boolean officialAsset(String url, String repo) {
        try {
            URI uri = new URI(url);
            return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost())
                    && uri.getPort() == -1 && uri.getUserInfo() == null && uri.getQuery() == null
                    && uri.getFragment() == null && uri.getRawPath().startsWith("/" + repo + "/releases/download/")
                    && !uri.getRawPath().contains("..") && !uri.getRawPath().contains("%");
        } catch (Exception e) { return false; }
    }
    static boolean downloadHost(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1
                    && ("github.com".equals(host) || "api.github.com".equals(host)
                    || "release-assets.githubusercontent.com".equals(host)
                    || "objects.githubusercontent.com".equals(host));
        } catch (Exception e) { return false; }
    }
    static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte value : bytes) text.append(String.format(Locale.ROOT, "%02x", value & 255));
        return text.toString();
    }
    static boolean sameSignature(byte[] installed, byte[] downloaded) {
        return MessageDigest.isEqual(installed, downloaded);
    }
}
