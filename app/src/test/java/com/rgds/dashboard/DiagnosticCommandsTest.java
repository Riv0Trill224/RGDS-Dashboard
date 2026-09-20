package com.rgds.dashboard;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Runs the actual POSIX scripts against synthetic sysfs trees, never the host's sysfs. */
public class DiagnosticCommandsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private void write(File directory, String name, String value) throws Exception {
        Files.createDirectories(directory.toPath());
        Files.write(new File(directory, name).toPath(), value.getBytes(StandardCharsets.UTF_8));
    }
    private CommandResult execute(String script, String systemPath, File fixture) throws Exception {
        String shell = System.getenv("RGDS_TEST_SHELL");
        if (shell == null || shell.isEmpty()) shell = "sh";
        String path = fixture.getAbsolutePath().replace('\\', '/');
        script = script.replace(systemPath, "'" + path.replace("'", "'\\''") + "'");
        File scriptFile = temp.newFile("probe-" + System.nanoTime() + ".sh");
        Files.write(scriptFile.toPath(), ("PATH=/usr/bin:/bin:$PATH; export PATH\n" + script).getBytes(StandardCharsets.UTF_8));
        File stdout = temp.newFile(), stderr = temp.newFile();
        ProcessBuilder builder = new ProcessBuilder(shell, scriptFile.getAbsolutePath().replace('\\', '/'))
                .redirectOutput(stdout).redirectError(stderr);
        Process process = builder.start();
        try {
            assertTrue("sysfs script timeout", process.waitFor(5, TimeUnit.SECONDS));
            return new CommandResult(new String(Files.readAllBytes(stdout.toPath()), StandardCharsets.UTF_8),
                    new String(Files.readAllBytes(stderr.toPath()), StandardCharsets.UTF_8),
                    process.exitValue(), false, false, false, false);
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
    @Test public void enumeratesThermalTypesAndPreservesRawTemperatures() throws Exception {
        File tree = temp.newFolder();
        write(new File(tree, "thermal_zone0"), "type", "soc-thermal\n");
        write(new File(tree, "thermal_zone0"), "temp", "43100\n");
        write(new File(tree, "thermal_zone7"), "type", "battery\n");
        write(new File(tree, "thermal_zone7"), "temp", "unknown\n");
        CommandResult result = execute(DiagnosticCommands.THERMAL, "/sys/class/thermal", tree);
        assertEquals(result.stderr, 0, result.exitCode);
        assertTrue(result.stdout.contains("type: soc-thermal"));
        assertTrue(result.stdout.contains("temp: 43100"));
        assertTrue(result.stdout.contains("temp: unknown"));
    }
    @Test public void missingCpuFileDoesNotHideOtherCoresAndProducesError() throws Exception {
        File tree = temp.newFolder();
        write(new File(tree, "cpu0/cpufreq"), "scaling_cur_freq", "1800000\n");
        write(new File(tree, "cpu7/cpufreq"), "scaling_cur_freq", "600000\n");
        CommandResult result = execute(DiagnosticCommands.CPU_FREQ, "/sys/devices/system/cpu", tree);
        assertEquals(1, result.exitCode);
        assertTrue(result.stdout.contains("1800000"));
        assertTrue(result.stdout.contains("cpu7"));
        assertTrue(result.stdout.contains("600000"));
        assertTrue(result.stderr.contains("cpuinfo_cur_freq"));
    }
    @Test public void devfreqDoesNotAssumeVendorNameOrLoadAvailability() throws Exception {
        File tree = temp.newFolder();
        write(new File(tree, "arbitrary-gpu"), "cur_freq", "800000000\n");
        write(new File(tree, "memory-controller"), "name", "dmc\n");
        CommandResult result = execute(DiagnosticCommands.DEVFREQ, "/sys/class/devfreq", tree);
        assertEquals(result.stderr, 0, result.exitCode);
        assertTrue(result.stdout.contains("800000000"));
        assertTrue(result.stdout.contains("name RAW: dmc"));
    }
    @Test public void gpuSearchStopsAtDepthThree() throws Exception {
        File tree = temp.newFolder();
        write(new File(tree, "soc/bus/mali-gpu"), "load", "42\n");
        write(new File(tree, "soc/bus/other/deep-gpu"), "load", "99\n");
        CommandResult result = execute(DiagnosticCommands.GPU_SCAN, "/sys/devices/platform", tree);
        assertEquals(result.stderr, 0, result.exitCode);
        assertTrue(result.stdout.contains("mali-gpu"));
        assertTrue(result.stdout.contains("load RAW: 42"));
        assertFalse(result.stdout.contains("deep-gpu"));
    }
    @Test public void gpuSearchStopsAfter256VisitedDirectories() throws Exception {
        File tree = temp.newFolder();
        for (int i = 0; i < 270; i++) Files.createDirectories(new File(tree, "node" + i).toPath());
        CommandResult result = execute(DiagnosticCommands.GPU_SCAN, "/sys/devices/platform", tree);
        assertEquals(result.stderr, 0, result.exitCode);
        assertTrue(result.stdout.contains("visitados=256"));
    }
}
