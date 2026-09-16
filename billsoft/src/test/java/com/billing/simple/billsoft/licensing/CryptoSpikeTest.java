package com.billing.simple.billsoft.licensing;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class CryptoSpikeTest {

    @Test
    void testEd25519KeyGenAndSignVerify() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        PublicKey pubKey = kp.getPublic();
        PrivateKey privKey = kp.getPrivate();

        String pubBase64 = Base64.getEncoder().encodeToString(pubKey.getEncoded());
        String privBase64 = Base64.getEncoder().encodeToString(privKey.getEncoded());

        System.out.println("=== SAMPLE ED25519 MASTER KEYPAIR ===");
        System.out.println("PUBLIC_KEY:  " + pubBase64);
        System.out.println("PRIVATE_KEY: " + privBase64);
        System.out.println("======================================");

        assertNotNull(pubBase64);
        assertNotNull(privBase64);

        // Sign test payload
        String canonicalPayload = "1\nLIC-001\nK7XM-92QP-4B9R-XD6T\nABC Traders\nRupeeCRM\nPRO\nSILVER\nACTIVE\n1\n2026-09-14T12:00:00Z\n2029-09-14T23:59:59Z";

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privKey);
        signer.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        byte[] sigBytes = signer.sign();
        String sigBase64 = Base64.getEncoder().encodeToString(sigBytes);

        // Verify from raw Base64 public key
        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        PublicKey reconstructedPub = kf.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pubBase64)));

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(reconstructedPub);
        verifier.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(sigBase64)));

        // Tamper test
        String tamperedPayload = "1\nLIC-001\nK7XM-92QP-4B9R-XD6T\nABC Traders\nRupeeCRM\nPRO\nGOLD\nACTIVE\n1\n2026-09-14T12:00:00Z\n2029-09-14T23:59:59Z";
        Signature tamperVerifier = Signature.getInstance("Ed25519");
        tamperVerifier.initVerify(reconstructedPub);
        tamperVerifier.update(tamperedPayload.getBytes(StandardCharsets.UTF_8));
        assertFalse(tamperVerifier.verify(Base64.getDecoder().decode(sigBase64)));
    }
}
