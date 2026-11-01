package com.limelight.nvstream.quic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.Assert.*;

public class SyzygyQuicAuthenticationTest {
    @Test
    public void helloUsesSq1JsonLine() {
        JsonObject hello = JsonParser.parseString(
                SyzygyQuicAuthentication.clientHelloLine().trim()).getAsJsonObject();
        assertEquals(1, hello.get("hello").getAsInt());
        assertEquals(1, hello.size());
    }

    @Test
    public void clientProofSignsExactSq1DomainAndChallenge() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        byte[] challenge = new byte[32];
        for (int i = 0; i < challenge.length; i++) challenge[i] = (byte) (i * 7);
        String pem = "-----BEGIN CERTIFICATE-----\nTEST-CERT\n-----END CERTIFICATE-----";
        String line = SyzygyQuicAuthentication.clientProofLine(
                Base64.getEncoder().encodeToString(challenge), pem, pair.getPrivate());

        JsonObject response = JsonParser.parseString(line.trim()).getAsJsonObject();
        assertEquals(pem, response.get("certificate").getAsString());
        byte[] signature = Base64.getDecoder().decode(response.get("signature").getAsString());

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pair.getPublic());
        verifier.update("Syzygy QUIC client authentication v1\0".getBytes(StandardCharsets.US_ASCII));
        verifier.update(challenge);
        assertTrue(verifier.verify(signature));
    }

    @Test
    public void rejectsWrongChallengeLength() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        try {
            SyzygyQuicAuthentication.clientProofLine(
                    Base64.getEncoder().encodeToString(new byte[31]), "cert",
                    generator.generateKeyPair().getPrivate());
            fail("Expected invalid challenge rejection");
        } catch (java.security.GeneralSecurityException expected) {
            // Expected.
        }
    }

    @Test
    public void authorizationRequiresExactTrueResponse() {
        assertTrue(SyzygyQuicAuthentication.isAuthorizedLine("{\"authorized\":true}"));
        assertFalse(SyzygyQuicAuthentication.isAuthorizedLine("{\"authorized\":false}"));
        assertFalse(SyzygyQuicAuthentication.isAuthorizedLine("{\"authorized\":true,\"extra\":1}"));
        assertFalse(SyzygyQuicAuthentication.isAuthorizedLine("not json"));
        assertFalse(SyzygyQuicAuthentication.isAuthorizedLine(null));
    }
}
