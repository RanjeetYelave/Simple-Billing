package com.billing.simple.billsoft.licensing;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class RawKeyConversionTest {

    // Standard 12-byte ASN.1 prefix for Ed25519 SubjectPublicKeyInfo in X.509
    private static final byte[] ED25519_X509_PREFIX = new byte[]{
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    @Test
    void testRaw32BytePublicKeyReconstruction() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        byte[] fullX509Bytes = kp.getPublic().getEncoded();
        assertEquals(44, fullX509Bytes.length); // 12 bytes prefix + 32 bytes raw pub key

        // Extract raw 32-byte public key (as TweetNaCl returns)
        byte[] raw32Bytes = Arrays.copyOfRange(fullX509Bytes, 12, 44);
        assertEquals(32, raw32Bytes.length);
        String raw32Base64 = Base64.getEncoder().encodeToString(raw32Bytes);

        // Reconstruct X.509 from raw 32 bytes
        byte[] reconstructedX509 = new byte[44];
        System.arraycopy(ED25519_X509_PREFIX, 0, reconstructedX509, 0, 12);
        System.arraycopy(Base64.getDecoder().decode(raw32Base64), 0, reconstructedX509, 12, 32);

        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        PublicKey reconstructedPub = kf.generatePublic(new X509EncodedKeySpec(reconstructedX509));

        // Verify that signatures generated with the private key verify against reconstructedPub
        String msg = "Hello Ed25519 Cross Platform";
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(kp.getPrivate());
        signer.update(msg.getBytes(StandardCharsets.UTF_8));
        byte[] sig = signer.sign();

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(reconstructedPub);
        verifier.update(msg.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(sig));
    }
}
