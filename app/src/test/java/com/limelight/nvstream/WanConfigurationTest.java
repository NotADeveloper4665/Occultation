package com.limelight.nvstream;
import org.junit.Test;
import static org.junit.Assert.*;
public class WanConfigurationTest {
    @Test public void wanIsOptInAndSessionLocal() {
        StreamConfiguration normal = new StreamConfiguration.Builder().build();
        StreamConfiguration wan = new StreamConfiguration.Builder().setWanOptimization(true).setMaxPacketSize(1024).build();
        assertFalse(normal.getWanOptimization());
        assertTrue(wan.getWanOptimization());
        assertEquals(1024, wan.getMaxPacketSize());
        assertFalse(normal.getWanOptimization());
    }
}
