package com.rgds.dashboard;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class SessionLogTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @Test public void exportsPersistentSnapshotAndCapsBytes() throws Exception {
        SessionLog log = new SessionLog(temp.newFolder());
        log.write("root denied");
        assertTrue(new String(log.snapshot(), StandardCharsets.UTF_8).contains("root denied"));
        log.write(new String(new char[3 * 1024 * 1024]).replace('\0', 'x'));
        log.write("extra");
        assertTrue(log.snapshot().length <= 2 * 1024 * 1024);
        assertTrue(new String(log.snapshot(), StandardCharsets.UTF_8).contains("LOG_LIMIT_REACHED"));
        assertTrue(log.status().contains("límite"));
    }
    @Test public void retainsAtMostTenSessions() throws Exception {
        File directory = temp.newFolder();
        for (int i = 0; i < 12; i++) new SessionLog(directory).write("session");
        assertEquals(10, directory.listFiles().length);
    }
}
