package com.rgds.dashboard;
import org.junit.Test;
import static org.junit.Assert.*;

public class SurfaceCatalogTest {
    private SurfaceCatalog catalog(String raw) {
        return new SurfaceCatalog(new CommandResult(raw, "", 0, false, false, false, false),
                "com.mojang.minecraftpe", "com.rgds.dashboard");
    }
    @Test public void keepsUnattributedLayersAndRanksExactPackageFirst() {
        SurfaceCatalog c = catalog("SurfaceView - NativeRenderer$1\ncom.mojang.minecraftpe.fake/Main\n"
                + "com.rgds.dashboard/Main\nSurfaceView[com.mojang.minecraftpe/Main]#5\n"
                + "SurfaceView - NativeRenderer$1\n");
        assertEquals(3, c.layers.size());
        assertEquals(1, c.matches);
        assertEquals(1, c.own);
        assertTrue(c.layers.get(0).contains("minecraftpe/Main"));
        assertEquals("SurfaceView - NativeRenderer$1", c.layers.get(1));
    }
    @Test public void noPackageMatchStillAllowsManualChoice() {
        SurfaceCatalog c = catalog("SurfaceView - GameRenderer\n");
        assertEquals(0, c.matches);
        assertEquals(1, c.layers.size());
    }
    @Test public void distinguishesErrorsEmptyAndRejectedNames() {
        assertNull(catalog("").error);
        assertEquals(0, catalog("").layers.size());
        assertEquals(1, catalog("unsafe'quote").rejected);
        assertNotNull(catalog("Permission Denial: can't dump SurfaceFlinger").error);
        assertNotNull(new SurfaceCatalog(new CommandResult("", "denied", 1, false, false, false, false), "game", "self").error);
        assertNotNull(new SurfaceCatalog(new CommandResult("partial", "", 0, false, false, true, false), "game", "self").error);
    }
}
