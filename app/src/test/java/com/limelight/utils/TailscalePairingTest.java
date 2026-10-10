package com.limelight.utils;

import android.content.ContextWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.net.InetAddress;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TailscalePairingTest {
    @Test public void acceptsOnlyTailscaleAddressRanges() throws Exception {
        for (String address : new String[]{"100.64.0.1", "100.127.255.254", "fd7a:115c:a1e0::1"})
            assertTrue(TailscalePairing.isTailscaleAddress(InetAddress.getByName(address).getAddress()));
        for (String address : new String[]{"100.63.255.255", "100.128.0.1", "192.168.1.2", "::1", "fd7a:115c:a1e1::1"})
            assertFalse(TailscalePairing.isTailscaleAddress(InetAddress.getByName(address).getAddress()));
    }
    @Test public void failsClosedWithoutNetworkManager() throws Exception {
        ContextWrapper noNetwork = new ContextWrapper(null) {
            @Override public Object getSystemService(String name) { return null; }
        };
        assertFalse(TailscalePairing.protectedConnection(noNetwork, "100.64.0.1"));
    }
}
