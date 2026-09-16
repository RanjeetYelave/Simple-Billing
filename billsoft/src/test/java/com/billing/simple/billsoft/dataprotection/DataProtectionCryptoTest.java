package com.billing.simple.billsoft.dataprotection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class DataProtectionCryptoTest {

    private DataProtectionCrypto crypto;

    @BeforeEach
    public void setUp() {
        crypto = new DataProtectionCrypto();
    }

    @Test
    public void testDeterministicKeyDerivation() {
        SecretKey key1 = crypto.deriveKeyFromLicenseId("LIC-TEST-12345");
        SecretKey key2 = crypto.deriveKeyFromLicenseId("LIC-TEST-12345");
        SecretKey key3 = crypto.deriveKeyFromLicenseId("LIC-DIFFERENT-999");

        assertNotNull(key1);
        assertNotNull(key2);
        assertArrayEquals(key1.getEncoded(), key2.getEncoded(), "Same license ID must derive identical key");
        assertFalse(java.util.Arrays.equals(key1.getEncoded(), key3.getEncoded()), "Different license ID must derive different key");
    }

    @Test
    public void testCompressionAndEncryptionRoundTrip() throws Exception {
        String testJson = "{\"invoices\":[{\"id\":101,\"customer\":\"Ranjeet\",\"amount\":45000.50}],\"firmName\":\"Test Enterprise\"}";
        byte[] rawBytes = testJson.getBytes(StandardCharsets.UTF_8);

        SecretKey key = crypto.deriveKeyFromLicenseId("LIC-VAULT-882");
        byte[] container = crypto.encryptBackup(rawBytes, key);

        assertNotNull(container);
        assertTrue(container.length > 17);
        assertEquals('R', (char) container[0]);
        assertEquals('C', (char) container[1]);
        assertEquals('B', (char) container[2]);
        assertEquals('P', (char) container[3]);
        assertEquals(0x01, container[4]);

        byte[] decryptedBytes = crypto.decryptBackup(container, key);
        String decryptedJson = new String(decryptedBytes, StandardCharsets.UTF_8);

        assertEquals(testJson, decryptedJson);
    }

    @Test
    public void testTamperAndCorruptionDetection() throws Exception {
        String testJson = "{\"secret\":\"confidential_records\"}";
        byte[] rawBytes = testJson.getBytes(StandardCharsets.UTF_8);
        SecretKey key = crypto.deriveKeyFromLicenseId("LIC-SECURE-99");
        byte[] container = crypto.encryptBackup(rawBytes, key);

        // Corrupt 1 byte in ciphertext / auth tag
        container[container.length - 5] ^= 0xFF;

        assertThrows(Exception.class, () -> {
            crypto.decryptBackup(container, key);
        }, "Tampered or corrupted ciphertext must fail GCM authentication tag check");
    }

    @Test
    public void testInvalidMagicHeaderDetection() {
        byte[] corruptHeader = new byte[]{ 'X', 'Y', 'Z', 'W', 0x01, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4 };
        SecretKey key = crypto.deriveKeyFromLicenseId("LIC-HEADER-TEST");

        assertThrows(IllegalArgumentException.class, () -> {
            crypto.decryptBackup(corruptHeader, key);
        });
    }

    @Test
    public void testObfuscatedTokenReconstruction() {
        String token = VaultTransportRegistry.resolveDefaultDescriptor();
        assertNotNull(token);
        assertEquals("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", token);
    }
}
