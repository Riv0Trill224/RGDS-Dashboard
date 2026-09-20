package com.rgds.dashboard;

import org.junit.Test;
import static org.junit.Assert.*;

public class CpuUsageTest {
    @Test public void measuresDeltasWithoutDoubleCountingGuest() {
        CpuUsage cpu = new CpuUsage();
        assertNull(cpu.sample("cpu 100 0 100 800 0 0 0 0 30 0"));
        assertEquals(50f, cpu.sample("cpu 125 0 125 850 0 0 0 0 50 0"), 0.001f);
    }
    @Test public void unavailableMalformedAndResetDoNotProduceFakeValues() {
        CpuUsage cpu = new CpuUsage();
        assertNull(cpu.sample(null));
        assertNull(cpu.sample("cpu nope"));
        assertNull(cpu.sample("cpu 1 0 0 9"));
        assertNull(cpu.sample("cpu 1 0 0 9"));
        assertNull(cpu.sample("cpu 0 0 0 0"));
        cpu.reset();
        assertNull(cpu.sample("cpu 20 0 0 80"));
        assertEquals(0f, cpu.sample("cpu 20 0 0 180"), 0.001f);
    }
    @Test public void treatsIoWaitAsIdleAndRejectsBadCounters() {
        CpuUsage cpu = new CpuUsage();
        assertNull(cpu.sample("cpu 10 0 0 80 10"));
        assertEquals(50f, cpu.sample("cpu 60 0 0 100 40"), 0.001f);
        assertNull(cpu.sample("cpu -1 0 0 100"));
    }
}
