package com.limelight.nvstream.quic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * SQ1 client authentication primitives shared with Syzygy's experimental QUIC relay.
 *
 * This class does not create a QUIC connection or validate the TLS handshake itself.
 * Call verifyHostCertificate() against the certificate presented by the QUIC engine
 * before sending the hello message or accepting any application data.
 */
public final class SyzygyQuicAuthentication {
    private static final byte[] DOMAIN =
            "Syzygy QUIC client authentication v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final int CHALLENGE_BYTES = 32;

    private SyzygyQuicAuthentication() {}

    public static String clientHelloLine() {
        JsonObject hello = new JsonObject();
        hello.addProperty("hello", 1);
        return hello.toString() + "\n";
    }

    /**
     * Build the signed client response to the server's base64-encoded 32-byte challenge.
     * The certificate must be the exact PEM certificate registered with Syzygy.
     */
    public static String clientProofLine(String challengeBase64, String certificatePem,
                                         PrivateKey privateKey) throws GeneralSecurityException {
        if (challengeBase64 == null || certificatePem == null || privateKey == null) {
            throw new IllegalArgumentException("Missing SQ1 client identity material");
        }
        if (!"RSA".equalsIgnoreCase(privateKey.getAlgorithm())) {
            throw new GeneralSecurityException("SQ1 currently requires an RSA client key");
        }
        final byte[] challenge;
        try {
            challenge = Base64.getDecoder().decode(challengeBase64);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Invalid SQ1 challenge encoding", e);
        }
        if (challenge.length != CHALLENGE_BYTES) {
            throw new GeneralSecurityException("Invalid SQ1 challenge length");
        }

        byte[] signed = new byte[DOMAIN.length + challenge.length];
        System.arraycopy(DOMAIN, 0, signed, 0, DOMAIN.length);
        System.arraycopy(challenge, 0, signed, DOMAIN.length, challenge.length);

        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(signed);
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        JsonObject response = new JsonObject();
        response.addProperty("certificate", certificatePem);
        response.addProperty("signature", signature);
        return response.toString() + "\n";
    }

    /** Exact SHA-256 DER-certificate pin check used before SQ1 application authentication. */
    public static boolean verifyHostCertificate(X509Certificate certificate, String expectedFingerprintHex)
            throws GeneralSecurityException {
        if (certificate == null || expectedFingerprintHex == null ||
                !expectedFingerprintHex.matches("(?i)[0-9a-f]{64}")) {
            return false;
        }
        certificate.checkValidity();
        byte[] expected = decodeHex(expectedFingerprintHex);
        byte[] actual = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        return MessageDigest.isEqual(expected, actual);
    }

    /** Returns true only for the exact SQ1 authorization response object. */
    public static boolean isAuthorizedLine(String line) {
        if (line == null || line.length() > 4096) return false;
        try {
            JsonObject object = JsonParser.parseString(line).getAsJsonObject();
            return object.size() == 1 && object.has("authorized") &&
                    object.get("authorized").isJsonPrimitive() &&
                    object.get("authorized").getAsJsonPrimitive().isBoolean() &&
                    object.get("authorized").getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] decodeHex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
