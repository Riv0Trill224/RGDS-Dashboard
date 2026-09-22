package com.rgds.dashboard;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdatePolicyTest {
    private final String repo = "Riv0Trill224/RGDS-Dashboard";
    @Test public void acceptsOnlyOfficialHttpsReleaseAsset() {
        assertTrue(UpdatePolicy.officialAsset("https://github.com/" + repo + "/releases/download/v0.4/RGDS-Dashboard.apk", repo));
        for (String url : new String[]{"http://github.com/" + repo + "/releases/download/a/b",
                "https://github.com/other/repo/releases/download/a/b", "https://github.com.evil.test/" + repo + "/releases/download/a/b",
                "https://github.com/" + repo + "/releases/download/../../secret", "https://github.com/" + repo + "/releases/download/%2e%2e/b"})
            assertFalse(url, UpdatePolicy.officialAsset(url, repo));
    }
    @Test public void redirectsStayOnKnownHttpsHosts() {
        assertTrue(UpdatePolicy.downloadHost("https://release-assets.githubusercontent.com/file?signature=example"));
        assertFalse(UpdatePolicy.downloadHost("https://evil.test/payload"));
        assertFalse(UpdatePolicy.downloadHost("http://github.com/file"));
        assertFalse(UpdatePolicy.downloadHost("https://user@github.com/file"));
    }
    @Test public void certificateBytesMustMatch() {
        assertTrue(UpdatePolicy.sameSignature(new byte[]{1,2}, new byte[]{1,2}));
        assertFalse(UpdatePolicy.sameSignature(new byte[]{1,2}, new byte[]{1,3}));
    }
}
