package com.rgds.dashboard;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReportTransportTest {
    @Test public void rejectsLegacyAckWrongIdAndFalseMailClaims() {
        assertFalse(ReportTransport.accepted(true,"a","","",false));
        assertFalse(ReportTransport.accepted(true,"a","b","worker_received",true));
        assertFalse(ReportTransport.accepted(true,"a","a","smtp_accepted",true));
        assertFalse(ReportTransport.accepted(true,"a","a","worker_received",false));
        assertTrue(ReportTransport.accepted(true,"a","a","worker_received",true));
        assertTrue(ReportTransport.accepted(false,"a","a","smtp_accepted",false));
    }
    @Test public void boundedExcerptPreservesRecentDiagnostic() {
        String text = new String(new char[20000]).replace('\0','a') + "FPS_DIAGNOSTIC";
        assertEquals(16000, ReportTransport.excerpt(text).length());
        assertTrue(ReportTransport.excerpt(text).endsWith("FPS_DIAGNOSTIC"));
        assertEquals("short",ReportTransport.excerpt("short"));
    }
}
