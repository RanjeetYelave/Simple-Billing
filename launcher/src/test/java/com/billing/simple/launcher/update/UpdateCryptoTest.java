package com.billing.simple.launcher.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCryptoTest {

    // Known test Ed25519 public key from launcher constant
    private static final String PINNED_PUBKEY = "MCowBQYDK2VwAyEAdmc4i0VRQ4Whs4OqCfxuOWSiiLQiiyp8VlTRhBTGRZo=";

    @Test
    void testDecodePinnedPublicKey() throws Exception {
        PublicKey key = UpdateCrypto.decodePublicKey(PINNED_PUBKEY);
        assertNotNull(key);
        assertTrue("EdDSA".equalsIgnoreCase(key.getAlgorithm()) || "Ed25519".equalsIgnoreCase(key.getAlgorithm()));
        assertEquals("X.509", key.getFormat());
    }

    @Test
    void testSignAndVerifyRoundTrip() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();

        byte[] payload = "{\"appVersion\":\"1.0.5\"}".getBytes(StandardCharsets.UTF_8);
        String signature = UpdateCrypto.sign(payload, kp.getPrivate());

        assertNotNull(signature);
        assertFalse(signature.isEmpty());

        // Valid signature verifies true
        assertTrue(UpdateCrypto.verifySignature(payload, signature, kp.getPublic()));

        // Tampered payload fails
        byte[] tampered = "{\"appVersion\":\"1.0.6\"}".getBytes(StandardCharsets.UTF_8);
        assertFalse(UpdateCrypto.verifySignature(tampered, signature, kp.getPublic()));

        // Tampered signature fails
        assertFalse(UpdateCrypto.verifySignature(payload, signature.substring(0, signature.length() - 4) + "AAAA", kp.getPublic()));

        // Wrong public key fails
        KeyPair anotherKp = kpg.generateKeyPair();
        assertFalse(UpdateCrypto.verifySignature(payload, signature, anotherKp.getPublic()));

        // Null / empty inputs return false safely
        assertFalse(UpdateCrypto.verifySignature(null, signature, kp.getPublic()));
        assertFalse(UpdateCrypto.verifySignature(payload, null, kp.getPublic()));
        assertFalse(UpdateCrypto.verifySignature(payload, "", kp.getPublic()));
        assertFalse(UpdateCrypto.verifySignature(payload, signature, null));
    }

    @Test
    void testComputeSha256(@TempDir Path tempDir) throws Exception {
        byte[] testBytes = "Hello RupeeCRM OTA Update Verification".getBytes(StandardCharsets.UTF_8);
        String hashFromBytes = UpdateCrypto.computeSha256(testBytes);

        assertEquals(64, hashFromBytes.length());

        Path tempFile = tempDir.resolve("test-artifact.bin");
        Files.write(tempFile, testBytes);

        String hashFromFile = UpdateCrypto.computeSha256(tempFile);
        assertEquals(hashFromBytes, hashFromFile);

        // Verification with known SHA-256 for empty byte array:
        // e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", UpdateCrypto.computeSha256(new byte[0]));
    }

    @Test
    void testZipMagicValidation(@TempDir Path tempDir) throws Exception {
        Path validZip = tempDir.resolve("valid.war");
        byte[] zipBytes = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00};
        Files.write(validZip, zipBytes);
        assertTrue(UpdateCrypto.isZipFile(validZip));

        Path invalidZip = tempDir.resolve("invalid.war");
        byte[] textBytes = "This is definitely not a zip file".getBytes(StandardCharsets.UTF_8);
        Files.write(invalidZip, textBytes);
        assertFalse(UpdateCrypto.isZipFile(invalidZip));

        Path shortFile = tempDir.resolve("short.war");
        Files.write(shortFile, new byte[]{0x50, 0x4B});
        assertFalse(UpdateCrypto.isZipFile(shortFile));

        Path nonExistent = tempDir.resolve("does_not_exist.war");
        assertFalse(UpdateCrypto.isZipFile(nonExistent));
    }
}
