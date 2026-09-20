package com.rgds.dashboard;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class RootShellTest {
    private static Process child(String mode) throws IOException {
        String java = new File(System.getProperty("java.home"), "bin/java").getPath();
        String classes;
        try { classes = new File(CommandFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath(); }
        catch (Exception e) { throw new IOException(e); }
        return new ProcessBuilder(java, "-cp", classes, CommandFixture.class.getName(), mode).start();
    }
    private static RootShell shell(String mode) {
        return new RootShell(args -> child(mode), 5000, 5000, 8192);
    }
    @Test public void checksActualRootUidAndDenial() {
        assertTrue(shell("root").isRootAvailable());
        assertFalse(shell("user").isRootAvailable());
        CommandResult denied = shell("denied").checkRoot();
        assertEquals("NO AUTORIZADO", RootShell.rootStatus(denied));
        assertEquals(1, denied.exitCode);
        assertTrue(denied.stderr.contains("Permission denied"));
    }
    @Test public void distinguishesMissingSuFromDeniedAuthorization() {
        RootShell shell = new RootShell(args -> { throw new IOException("su missing"); }, 1000, 1000, 1024);
        CommandResult result = shell.checkRoot();
        assertEquals("NO DISPONIBLE", RootShell.rootStatus(result));
        assertTrue(result.startFailed);
        assertTrue(result.stderr.contains("IOException"));
    }
    @Test(timeout = 12000) public void concurrentlyDrainsBothPipesAndMarksOutputCap() {
        CommandResult result = shell("flood").runRootCommand("id");
        assertEquals(0, result.exitCode);
        assertFalse(result.timedOut);
        assertTrue(result.truncated);
        assertTrue(result.stdout.startsWith("stdout"));
        assertTrue(result.stderr.startsWith("stderr"));
        assertTrue(result.stdout.length() <= 8192);
        assertTrue(result.stderr.length() <= 8192);
    }
    @Test(timeout = 8000) public void timesOutAndTerminatesItsOwnProcess() throws Exception {
        AtomicReference<Process> process = new AtomicReference<>();
        RootShell shell = new RootShell(args -> {
            Process child = child("sleep"); process.set(child); return child;
        }, 700, 700, 1024);
        CommandResult result = shell.checkRoot();
        assertTrue(result.timedOut);
        assertFalse(result.succeeded());
        assertTrue(process.get().waitFor(2, TimeUnit.SECONDS));
    }
    @Test(timeout = 8000) public void interruptionCancelsProcessAndPreservesInterruptFlag() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Process> process = new AtomicReference<>();
        AtomicReference<CommandResult> result = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        RootShell shell = new RootShell(args -> {
            Process child = child("sleep"); process.set(child); started.countDown(); return child;
        }, 30000, 30000, 1024);
        Thread worker = new Thread(() -> {
            result.set(shell.checkRoot()); interrupted.set(Thread.currentThread().isInterrupted());
        });
        worker.start();
        assertTrue(started.await(2, TimeUnit.SECONDS));
        worker.interrupt();
        worker.join(3000);
        assertFalse(worker.isAlive());
        assertTrue(result.get().interrupted);
        assertTrue(interrupted.get());
        assertTrue(process.get().waitFor(2, TimeUnit.SECONDS));
    }
    @Test public void rejectsAnythingOutsideReadOnlyCatalogBeforeLaunching() {
        AtomicBoolean launched = new AtomicBoolean();
        RootShell shell = new RootShell(args -> { launched.set(true); throw new IOException(); }, 1000, 1000, 1024);
        assertFalse(shell.runRootCommand("setprop test.value 1").succeeded());
        assertFalse(launched.get());
    }
}
