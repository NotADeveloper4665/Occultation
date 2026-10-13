package com.limelight.nvstream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = {com.limelight.shadows.ShadowMoonBridge.class})
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
