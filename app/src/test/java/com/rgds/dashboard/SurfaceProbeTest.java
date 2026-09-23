package com.rgds.dashboard;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class SurfaceProbeTest {
    private CommandResult ok(String stdout) {
        return new CommandResult(stdout, "", 0, false, false, false, false);
    }
    @Test public void keepsEmptyOutputAndFullDumpSeparate() {
        List<String> calls = new ArrayList<>();
        SurfaceProbe.Report report = SurfaceProbe.collect(ok("uid=0(root)"), command -> {
            calls.add(command);
            assertTrue(DiagnosticCommands.isAllowed(command));
            return ok(command.endsWith("--list") ? "" : "Layer: game\n");
        });
        assertEquals(3, calls.size());
        assertTrue(report.summary.contains("stdout=0 caracteres"));
        assertTrue(report.raw.contains("Layer: game"));
        assertTrue(report.raw.contains("/system/bin/dumpsys SurfaceFlinger --list"));
        assertFalse(DiagnosticCommands.isAllowed("/system/bin/dumpsys SurfaceFlinger; id"));
    }
    @Test public void denialDoesNotRepeatRootPrompts() {
        SurfaceProbe.Report report = SurfaceProbe.collect(ok("uid=2000(shell)"), command -> {
            fail("No probes without confirmed root"); return ok("");
        });
        assertTrue(report.summary.contains("Autoriza root"));
        assertTrue(report.raw.contains("uid=2000"));
    }
    @Test public void preservesTimeoutErrorsAndPartialOutput() {
        SurfaceProbe.Report report = SurfaceProbe.collect(ok("uid=0(root)"), command ->
                new CommandResult("partial", "failure detail", -1, true, false, true, false));
        assertTrue(report.summary.contains("timeout=true"));
        assertTrue(report.summary.contains("truncado=true"));
        assertTrue(report.raw.contains("failure detail"));
        assertTrue(report.raw.contains("partial"));
    }
}
