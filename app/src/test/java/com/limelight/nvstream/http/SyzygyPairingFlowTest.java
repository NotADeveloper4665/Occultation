package com.limelight.nvstream.http;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SyzygyPairingFlowTest {
    private static final String KEY = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static String xml(String fields) { return "<root status_code=\"200\">" + fields + "</root>"; }
    private static byte[] pem(X509Certificate cert) throws Exception {
        return ("-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(cert.getEncoded()) + "\n-----END CERTIFICATE-----\n").getBytes(StandardCharsets.US_ASCII);
    }
    private static X509Certificate certificate(KeyPair keys) throws Exception {
        X500Name name = new X500Name("CN=Occultation test");
        return new JcaX509CertificateConverter().getCertificate(new JcaX509v3CertificateBuilder(name,
                BigInteger.ONE, new Date(0), new Date(4102444800000L), name, keys.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate())));
    }
    private void exercise(boolean corruptMessage, boolean corruptProof) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair(); X509Certificate cert = certificate(keys);
        byte[] clientPem = pem(cert), serverPem = clientPem;
        byte[] nonce = new byte[32], message = SyzygyPairing.message(nonce, "android-1", clientPem);
        LimelightCryptoProvider crypto = mock(LimelightCryptoProvider.class);
        when(crypto.getClientCertificate()).thenReturn(cert);
        when(crypto.getClientPrivateKey()).thenReturn(keys.getPrivate());
        when(crypto.getPemEncodedClientCertificate()).thenReturn(clientPem);
        NvHTTP http = mock(NvHTTP.class); when(http.getUniqueId()).thenReturn("android-1");
        byte[] suppliedMessage = message.clone(); if (corruptMessage) suppliedMessage[0] ^= 1;
        byte[] proof = SyzygyPairing.confirmation(KEY, message, serverPem); if (corruptProof) proof[0] ^= 1;
        when(http.executePairingCommand(startsWith("syzygyphase=challenge&"), eq(true))).thenReturn(xml(
                "<challenge>"+SyzygyPairing.hex(nonce)+"</challenge><authmessage>"+
                SyzygyPairing.hex(suppliedMessage)+"</authmessage><plaincert>"+SyzygyPairing.hex(serverPem)+"</plaincert>"));
        when(http.executePairingCommand(startsWith("syzygyphase=response&"), eq(true))).thenAnswer(call -> {
            String request = call.getArgument(0);
            assertTrue(request.contains("&syzygyproof=" + SyzygyPairing.hex(SyzygyPairing.proof(KEY, message))));
            String signature = request.substring(request.indexOf("&clientsignature=") + "&clientsignature=".length());
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(keys.getPublic()); verifier.update(message);
            assertTrue(verifier.verify(SyzygyPairing.decodeHex(signature, 512)));
            return xml("<paired>1</paired><serverproof>" + SyzygyPairing.hex(proof) + "</serverproof>");
        });
        when(http.executePairingChallenge()).thenReturn(xml("<paired>1</paired>"));
        PairingManager pairing = new PairingManager(http, crypto);
        PairingManager.PairState result = pairing.pairWithSyzygyKey(KEY);
        if (corruptMessage || corruptProof) {
            assertEquals(PairingManager.PairState.FAILED, result); assertNull(pairing.getPairedCert());
            verify(http, never()).setServerCert(any()); verify(http, never()).executePairingChallenge();
            if (corruptMessage) verify(http, never()).executePairingCommand(startsWith("syzygyphase=response&"), eq(true));
        } else {
            assertEquals(PairingManager.PairState.PAIRED, result); assertEquals(cert, pairing.getPairedCert());
            org.mockito.InOrder order = inOrder(http);
            order.verify(http).setServerCert(cert); order.verify(http).executePairingChallenge();
        }
    }
    @Test public void completesSignedPairingAndPinsConfirmedCertificate() throws Exception { exercise(false, false); }
    @Test public void neverSignsSubstitutedChallenge() throws Exception { exercise(true, false); }
    @Test public void refusesUnauthenticatedServerCertificate() throws Exception { exercise(false, true); }
}
