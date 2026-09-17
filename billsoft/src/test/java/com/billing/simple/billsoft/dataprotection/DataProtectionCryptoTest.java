package com.billing.simple.billsoft.dataprotection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class DataProtectionCryptoTest {

    private DataProtectionCrypto crypto;

    @BeforeEach
    public void setUp() {
        crypto = new DataProtectionCrypto();
    }

    @Test
    public void testDeterministicSha256KeyDerivation() throws Exception {
        String licId = "LIC-TEST-12345";
        SecretKey key1 = crypto.deriveKeyFromLicenseId(licId);
        SecretKey key2 = crypto.deriveKeyFromLicenseId("  LIC-TEST-12345  "); // test trim
        SecretKey key3 = crypto.deriveKeyFromLicenseId("LIC-DIFFERENT-999");

        assertNotNull(key1);
        assertNotNull(key2);
        assertEquals("AES", key1.getAlgorithm());
        assertEquals(32, key1.getEncoded().length); // 256 bits

        // Verify matches direct SHA-256
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] expectedBytes = sha.digest(licId.getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(expectedBytes, key1.getEncoded());
        assertArrayEquals(key1.getEncoded(), key2.getEncoded(), "Same license ID must derive identical key");
        assertFalse(Arrays.equals(key1.getEncoded(), key3.getEncoded()), "Different license ID must derive different key");
    }

    @Test
    public void testEncryptionAndDecryptionRoundTrip() throws Exception {
        String testJson = "{\"invoices\":[{\"id\":101,\"customer\":\"Ranjeet\",\"amount\":45000.50}],\"firmName\":\"Test Enterprise\"}";
        byte[] rawBytes = testJson.getBytes(StandardCharsets.UTF_8);

        SecretKey key = crypto.deriveKeyFromLicenseId("LIC-VAULT-882");
        byte[] container = crypto.encryptBackup(rawBytes, key);

        assertNotNull(container);
        // Envelope: 1 byte version + 12 bytes IV + rawBytes.length ciphertext + 16 bytes GCM tag
        int expectedLen = 1 + 12 + rawBytes.length + 16;
        assertEquals(expectedLen, container.length);
        assertEquals(0x01, container[0]); // Version 0x01

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

        // 1. Corrupt 1 byte in ciphertext / auth tag
        byte[] tamperedCiphertext = container.clone();
        tamperedCiphertext[tamperedCiphertext.length - 3] ^= 0xFF;
        assertThrows(Exception.class, () -> crypto.decryptBackup(tamperedCiphertext, key),
                "Tampered ciphertext/tag must fail GCM authentication check");

        // 2. Corrupt 1 byte in IV
        byte[] tamperedIv = container.clone();
        tamperedIv[3] ^= 0xFF;
        assertThrows(Exception.class, () -> crypto.decryptBackup(tamperedIv, key),
                "Tampered IV must fail GCM authentication check");
    }

    @Test
    public void testWrongLicenseIdDecryptionFailure() throws Exception {
        String testJson = "{\"firmName\":\"Alpha Enterprises\"}";
        byte[] rawBytes = testJson.getBytes(StandardCharsets.UTF_8);
        SecretKey correctKey = crypto.deriveKeyFromLicenseId("LIC-CORRECT-111");
        SecretKey wrongKey = crypto.deriveKeyFromLicenseId("LIC-WRONG-222");

        byte[] container = crypto.encryptBackup(rawBytes, correctKey);

        assertThrows(Exception.class, () -> crypto.decryptBackup(container, wrongKey),
                "Decryption with wrong license ID must fail authentication");
    }

    @Test
    public void testInvalidVersionDetection() {
        byte[] corruptVersion = new byte[35];
        corruptVersion[0] = 0x02; // Invalid version != 0x01
        SecretKey key = crypto.deriveKeyFromLicenseId("LIC-VERSION-TEST");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            crypto.decryptBackup(corruptVersion, key);
        });
        assertTrue(ex.getMessage().contains("Unsupported container version"));
    }

    @Test
    public void testTruncatedContainerDetection() {
        byte[] truncated = new byte[28]; // Minimum is 29 bytes
        truncated[0] = 0x01;
        SecretKey key = crypto.deriveKeyFromLicenseId("LIC-TRUNCATED-TEST");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            crypto.decryptBackup(truncated, key);
        });
        assertTrue(ex.getMessage().contains("minimum 29 bytes"));
    }

    @Test
    public void testCrossLanguageWebCryptoParitySimulation() throws Exception {
        // Simulates an exact payload created by Web Crypto in standalone HTML:
        // Key = SHA-256("LIC-BROWSER-CROSS-LANG")
        // IV = fixed 12 bytes
        // AES-GCM 128-bit tag
        // Envelope = [0x01 | 12B IV | ciphertext + tag]
        String licenseId = "LIC-BROWSER-CROSS-LANG";
        String sampleJson = "{\"app\":\"RupeeCRM\",\"status\":\"VALIDATED\",\"invoices\":[{\"id\":1}]}";
        byte[] jsonBytes = sampleJson.getBytes(StandardCharsets.UTF_8);

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = md.digest(licenseId.getBytes(StandardCharsets.UTF_8));
        SecretKey browserKey = new SecretKeySpec(keyBytes, "AES");

        byte[] fixedIv = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, browserKey, new GCMParameterSpec(128, fixedIv));
        byte[] browserCiphertext = cipher.doFinal(jsonBytes);

        byte[] browserPayload = new byte[1 + 12 + browserCiphertext.length];
        browserPayload[0] = 0x01;
        System.arraycopy(fixedIv, 0, browserPayload, 1, 12);
        System.arraycopy(browserCiphertext, 0, browserPayload, 13, browserCiphertext.length);

        // Decrypt using DataProtectionCrypto Java implementation
        byte[] decrypted = crypto.decryptBackup(browserPayload, crypto.deriveKeyFromLicenseId(licenseId));
        assertEquals(sampleJson, new String(decrypted, StandardCharsets.UTF_8));
    }

    @Test
    public void testVaultTransportDescriptorResolution() {
        String descriptor = VaultTransportRegistry.resolveDefaultDescriptor();
        assertNotNull(descriptor);
        assertFalse(descriptor.isBlank());
        assertEquals(93, descriptor.length());
        assertTrue(descriptor.startsWith("github_"));
    }
}
