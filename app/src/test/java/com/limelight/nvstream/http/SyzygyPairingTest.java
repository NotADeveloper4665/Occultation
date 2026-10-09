package com.limelight.nvstream.http;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import static org.junit.Assert.*;

public class SyzygyPairingTest {
    private static final String KEY = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    @Test public void matchesEclipseAndSyzygyWireVector() throws Exception {
        byte[] nonce = new byte[32]; Arrays.fill(nonce, (byte)'n');
        byte[] message = SyzygyPairing.message(nonce, "desktop-1", "client-certificate".getBytes(StandardCharsets.UTF_8));
        assertEquals("ad6cba19e5df7f3387606cca01ab009591bf25c9a452ee6f26016bd40ca2ce8a",
                SyzygyPairing.hex(MessageDigest.getInstance("SHA-256").digest(message)));
        assertEquals("af4e3810806f0ca196fb292997b38d1a6d68fdbeeaba1be853ba1303725604a2",
                SyzygyPairing.hex(SyzygyPairing.proof(KEY, message)));
    }
    @Test public void bindsProofToClientIdentityAndCertificate() throws Exception {
        byte[] nonce = new byte[32];
        byte[] original = SyzygyPairing.message(nonce, "client", new byte[]{1});
        assertFalse(MessageDigest.isEqual(original, SyzygyPairing.message(nonce, "other", new byte[]{1})));
        assertFalse(MessageDigest.isEqual(original, SyzygyPairing.message(nonce, "client", new byte[]{2})));
        nonce[0] = 1;
        assertFalse(MessageDigest.isEqual(original, SyzygyPairing.message(nonce, "client", new byte[]{1})));
    }
    @Test public void confirmationBindsServerCertificate() throws Exception {
        byte[] message = new byte[]{1,2};
        assertFalse(MessageDigest.isEqual(SyzygyPairing.confirmation(KEY, message, new byte[]{3}),
                SyzygyPairing.confirmation(KEY, message, new byte[]{4})));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsInvalidHex() {
        SyzygyPairing.decodeHex("zz", 32);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsOversizedResponse() {
        SyzygyPairing.decodeHex("0000", 1);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsWrongKeyLength() {
        SyzygyPairing.normalizeKey("abcd");
    }
    @Test public void acceptsPastedUppercaseKey() {
        assertEquals(KEY, SyzygyPairing.normalizeKey(" " + KEY.toUpperCase(java.util.Locale.ROOT) + "\n"));
    }
}
