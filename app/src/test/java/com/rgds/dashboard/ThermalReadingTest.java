package com.rgds.dashboard;

import org.junit.Test;
import static org.junit.Assert.*;

public class ThermalReadingTest {
    @Test public void choosesHottestValidSensorWithActualName() {
        ThermalReading r = ThermalReading.parse("/sys/class/thermal/thermal_zone0\ntype: battery\ntemp: 31000\n"
                + "/sys/class/thermal/thermal_zone7\ntype: soc\ntemp: 55000\n");
        assertEquals(55f, r.celsius, 0.01f);
        assertEquals("soc", r.source);
    }
    @Test public void rejectsMalformedNonFiniteOutOfRangeAndUnscopedReadings() {
        for (String value : new String[]{"NaN", "Infinity", "garbage", "200000", "-50000"})
            assertNull(ThermalReading.parse("/sys/class/thermal/thermal_zone0\ntemp: " + value).celsius);
        assertNull(ThermalReading.parse("temp: 43000").celsius);
        assertNull(ThermalReading.parse("[Sin zonas visibles]").celsius);
    }
    @Test public void missingTypeDoesNotReusePreviousSensorName() {
        ThermalReading r = ThermalReading.parse("/sys/class/thermal/thermal_zone0\ntype: battery\ntemp: 31000\n"
                + "/sys/class/thermal/thermal_zone2\ntemp: 48000\n");
        assertEquals("thermal_zone2", r.source);
    }
}
