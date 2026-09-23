package com.rgds.dashboard;
import org.junit.Test;
import static org.junit.Assert.*;

public class SurfaceFpsTest {
    @Test public void missingFpsExplainsWhatTheProbeReturned() {
        assertTrue(SurfaceFps.parse("", 5000000000L).status.contains("vacía"));
        assertTrue(SurfaceFps.parse("16666666\n", 5000000000L).status.contains("Sin historial"));
        assertTrue(SurfaceFps.parse("16666666\n0 0 0\n", 5000000000L).status.contains("ceros"));
        assertTrue(SurfaceFps.parse("0 9223372036854775807 0\n", 5000000000L).status.contains("pendientes"));
        assertTrue(SurfaceFps.parse("0 100 0\n", 5000000000L).status.contains("antiguos"));
    }
    @Test public void usesPresentationTimestampsNotRefreshPeriod() {
        FpsProvider.Reading r = SurfaceFps.parse("16666666\n1 1000000000 2\n1 1050000000 2\n1 1100000000 2\n", 1150000000L);
        assertEquals(20f, r.fps, 0.01f);
        assertNull(SurfaceFps.parse("16666666\n", 1150000000L).fps);
    }
    @Test public void stalePendingAndDuplicateFramesDoNotBecomeFakeFps() {
        assertNull(SurfaceFps.parse("1 100 2\n1 200 2\n1 300 2\n", 5000000000L).fps);
        assertNull(SurfaceFps.parse("1 9223372036854775807 2\n1 100 2\n1 100 2\n", 200).fps);
    }
    @Test public void rejectsShellInjection() {
        String good = "SurfaceView[com.example.game/Main](BLAST)#12";
        assertTrue(SurfaceFps.allowed(SurfaceFps.command(good)));
        for (String bad : new String[]{"x'; id; '", "$(id)", "`id`", "x\ny", "x|id", "x&cat /etc/passwd"})
            assertNull(SurfaceFps.command(bad));
    }
}
