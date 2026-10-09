package com.limelight.nvstream.http;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Wire-compatible with Syzygy's generated 192-bit shared connection keys. */
public final class SyzygyPairing {
    private SyzygyPairing() {}

    public static String normalizeKey(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{48}")) {
            throw new IllegalArgumentException("A Syzygy connection key must contain 48 hexadecimal characters");
        }
        return normalized;
    }

    public static byte[] decodeHex(String value, int maxBytes) {
        if (value == null || value.length() % 2 != 0 || value.length() > maxBytes * 2) {
            throw new IllegalArgumentException("Invalid pairing response");
        }
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid pairing response");
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    public static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[2 * i] = digits[(bytes[i] & 255) >>> 4];
            result[2 * i + 1] = digits[bytes[i] & 15];
        }
        return new String(result);
    }

    private static void field(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    public static byte[] message(byte[] nonce, String uniqueId, byte[] clientPem)
            throws GeneralSecurityException, IOException {
        byte[] id = uniqueId.getBytes(StandardCharsets.UTF_8);
        if (nonce.length != 32 || id.length == 0 || id.length > 256) {
            throw new IllegalArgumentException("Invalid pairing challenge");
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        out.write("Syzygy pairing v1".getBytes(StandardCharsets.US_ASCII));
        field(out, nonce);
        field(out, id);
        field(out, MessageDigest.getInstance("SHA-256").digest(clientPem));
        return buffer.toByteArray();
    }

    public static byte[] proof(String key, byte[] message) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        // The wire protocol uses the hexadecimal text itself as the HMAC key.
        mac.init(new SecretKeySpec(normalizeKey(key).getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
        return mac.doFinal(message);
    }

    public static byte[] confirmation(String key, byte[] message, byte[] serverPem)
            throws GeneralSecurityException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("Syzygy server confirmation v1".getBytes(StandardCharsets.US_ASCII));
        out.write(message);
        out.write(MessageDigest.getInstance("SHA-256").digest(serverPem));
        return proof(key, out.toByteArray());
    }
}
