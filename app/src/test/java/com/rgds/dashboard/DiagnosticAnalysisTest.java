package com.rgds.dashboard;

import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class DiagnosticAnalysisTest {
    private static CommandResult output(String text) {
        return new CommandResult(text, "", 0, false, false, false, false);
    }
    @Test public void retainsRawAndOnlyInterpretsRecognizableThermalValues() {
        String interpreted = DiagnosticAnalysis.thermal("thermal_zone3\ntype: soc\ntemp: 43100\n");
        assertTrue(interpreted.contains("temp: 43100"));
        assertTrue(interpreted.contains("°C"));
        assertTrue(DiagnosticAnalysis.temperature("43").startsWith("--"));
        assertTrue(DiagnosticAnalysis.temperature("NaN").startsWith("--"));
        assertTrue(DiagnosticAnalysis.temperature("999999999999999999999999").startsWith("--"));
        assertTrue(DiagnosticAnalysis.temperature("900000").startsWith("--"));
    }
    @Test public void doesNotBorrowDisplayOrPidFromAnotherWindow() {
        Map<String, CommandResult> results = new LinkedHashMap<>();
        results.put("WINDOW DUMPSYS", output("com.rgds.dashboard pid=30 displayId=0\ncom.mojang.minecraftpe/MainActivity\n"));
        String summary = DiagnosticAnalysis.minecraft(results);
        assertTrue(summary.contains("detectado: SÍ"));
        assertTrue(summary.contains("PID: --"));
        assertTrue(summary.contains("Display: --"));
    }
    @Test public void usesOnlyExplicitEvidenceAndRetainsSurfaceName() {
        Map<String, CommandResult> results = new LinkedHashMap<>();
        results.put("SURFACEFLINGER", output("SurfaceView[com.mojang.minecraftpe/MainActivity](BLAST)#42\nOther surface\n"));
        results.put("ACTIVITY DUMPSYS", output("ProcessRecord{abcd 1832:com.mojang.minecraftpe/u0a42}\n"));
        results.put("WINDOW DUMPSYS", output("com.mojang.minecraftpe/MainActivity mDisplayId=1\n"));
        String summary = DiagnosticAnalysis.minecraft(results);
        assertTrue(summary.contains("PID: 1832"));
        assertTrue(summary.contains("Display: 1"));
        assertTrue(summary.contains("Surface: SurfaceView[com.mojang.minecraftpe/MainActivity](BLAST)#42"));
    }
    @Test public void noDataIsInconclusiveAndSimilarPackageDoesNotMatch() {
        Map<String, CommandResult> results = new LinkedHashMap<>();
        results.put("SURFACEFLINGER", output("com.mojang.minecraftpe.fake/Main"));
        String summary = DiagnosticAnalysis.minecraft(results);
        assertTrue(summary.contains("detectado: NO"));
        assertTrue(summary.contains("Cobertura parcial"));
        assertTrue(summary.contains("Surface: --"));
    }
}
