package com.rgds.dashboard;
import org.junit.Test;
import static org.junit.Assert.*;

public class HwcFpsTest {
    private static final String GAME = "com.mojang.minecraftpe";
    // Same column layout as the console report, with synthetic IDs/handles.
    private static final String HEADER = "id | z | sf-type | hwc-type | handle | transform | blnd | source crop | frame | dataspace | mFps | name\n";
    private String row(String name, String rate) {
        return "80 | 0 | Device | Device | 0 | None | None | 0,0,640,480 | 0,0,640,480 | 8810000 | "
                + rate + " | SurfaceView[" + name + "/MainActivity]#1(BLAST Consumer)1 | 0x0\n";
    }
    private String dump(String name, String rate) {
        return "h/w composer state:\nHWC2 Version vendor\nDisplayId=0, Connector 1\n"
                + HEADER + row("com.rgds.dashboard", "6.0") + "DrmHwcLayer Dump:\n"
                + "fps=59.9\nDisplayId=1, Connector 2\nNumHwLayers=1, 640x480p60.00\n"
                + HEADER + "------+-----+-----------\n" + row(name, rate)
                + "DrmHwcLayer Dump:\nfps=34.916676\nDisplayCompositor[1] Dump:\nFPS=31.266598\n";
    }
    @Test public void readsGameLayerRatherThanHzDashboardOrGlobalAverage() {
        FpsProvider.Reading r = HwcFps.parse(dump(GAME, "34.9"), GAME);
        assertEquals(34.9f, r.fps, 0.001f);
        assertEquals("FPS compositor · HWC 1", r.status);
    }
    @Test public void refusesAmbiguousAndSimilarPackages() {
        assertNull(HwcFps.parse(dump(GAME + ".fake", "34.9"), GAME).fps);
        assertNull(HwcFps.parse(dump("prefix" + GAME, "34.9"), GAME).fps);
        String two = dump(GAME, "34.9") + "DisplayId=2, Connector 3\n" + HEADER + row(GAME, "50");
        assertNull(HwcFps.parse(two, GAME).fps);
    }
    @Test public void neverReplacesMissingDataWithRefreshOrCachedNumbers() {
        for (String rate : new String[]{"NaN", "Infinity", "-1", "1001", "", "N/A"})
            assertNull(HwcFps.parse(dump(GAME, rate), GAME).fps);
        assertNull(HwcFps.parse("60.00 Hz\nfps=34.916676\nFPS=31.266598\n", GAME).fps);
        assertNull(HwcFps.parse(dump(GAME, "34.9").replace("mFps", "refresh"), GAME).fps);
        assertEquals(0f, HwcFps.parse(dump(GAME, "0.0"), GAME).fps, 0f);
    }
    @Test public void worksForOtherGamesAndReorderedColumns() {
        FpsProvider.Reading r = HwcFps.parse("h/w composer state:\nDisplayId=3, Connector 4\n"
                + "name | mFps | id\norg.example.emulator/Main | 27.2 | 1\n", "org.example.emulator");
        assertEquals(27.2f, r.fps, 0.001f);
        assertNull(HwcFps.parse(dump(GAME, "34.9"), "").fps);
    }
}
