package com.billing.simple.billsoft.dataprotection;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Standard, lightweight AES-256-GCM backup encryption and deterministic
 * SHA-256 key derivation for RupeeCRM Data Protection.
 *
 * Binary Envelope Specification:
 * [0]     Version    : 0x01 (1 byte)
 * [1..12] IV         : 12-byte cryptographically random IV
 * [13..N] Ciphertext : AES-256-GCM ciphertext + 16-byte authentication tag
 * Minimum valid payload length = 1 + 12 + 16 = 29 bytes.
 */
public class DataProtectionCrypto {

    public static final byte CONTAINER_VERSION_1 = 0x01;
    public static final int GCM_IV_LENGTH = 12;
    public static final int GCM_TAG_LENGTH_BITS = 128;
    public static final int MIN_CONTAINER_LENGTH = 1 + GCM_IV_LENGTH + (GCM_TAG_LENGTH_BITS / 8); // 29 bytes

    private final SecureRandom secureRandom;

    public DataProtectionCrypto() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Derives a deterministic 256-bit AES key directly from the customer's License ID via SHA-256.
     * Trade-off: SHA-256 is fast and prioritizes simplicity and robust cross-language (Java &lt;-&gt; Web Crypto)
     * compatibility over password-stretching KDFs.
     */
    public SecretKey deriveKeyFromLicenseId(String licenseId) {
        if (licenseId == null || licenseId.isBlank()) {
            throw new IllegalArgumentException("License ID cannot be empty for key derivation");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(licenseId.trim().getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES-256 key via SHA-256: " + e.getMessage(), e);
        }
    }

    /**
     * Encrypts UTF-8 raw JSON bytes into a minimal versioned AES-256-GCM envelope:
     * [ Version (0x01) | 12-byte IV | Ciphertext + 16-byte GCM Tag ]
     */
    public byte[] encryptBackup(byte[] rawJsonBytes, SecretKey secretKey) throws Exception {
        if (rawJsonBytes == null || rawJsonBytes.length == 0) {
            throw new IllegalArgumentException("Backup data cannot be empty");
        }
        if (secretKey == null) {
            throw new IllegalArgumentException("Secret key cannot be null");
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
        byte[] ciphertext = cipher.doFinal(rawJsonBytes);

        byte[] container = new byte[1 + GCM_IV_LENGTH + ciphertext.length];
        container[0] = CONTAINER_VERSION_1;
        System.arraycopy(iv, 0, container, 1, GCM_IV_LENGTH);
        System.arraycopy(ciphertext, 0, container, 1 + GCM_IV_LENGTH, ciphertext.length);
        return container;
    }

    /**
     * Validates envelope header, extracts IV, and decrypts/authenticates AES-256-GCM ciphertext
     * returning the raw UTF-8 JSON bytes.
     */
    public byte[] decryptBackup(byte[] containerBytes, SecretKey secretKey) throws Exception {
        if (containerBytes == null || containerBytes.length < MIN_CONTAINER_LENGTH) {
            throw new IllegalArgumentException("Corrupt or incomplete container bytes (minimum 29 bytes required)");
        }
        if (secretKey == null) {
            throw new IllegalArgumentException("Secret key cannot be null");
        }

        // 1. Verify Version
        byte version = containerBytes[0];
        if (version != CONTAINER_VERSION_1) {
            throw new IllegalArgumentException("Unsupported container version: " + version);
        }

        // 2. Extract IV (12 bytes)
        byte[] iv = Arrays.copyOfRange(containerBytes, 1, 1 + GCM_IV_LENGTH);

        // 3. Extract Ciphertext & Tag
        byte[] ciphertext = Arrays.copyOfRange(containerBytes, 1 + GCM_IV_LENGTH, containerBytes.length);

        // 4. Decrypt & Authenticate GCM
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        return cipher.doFinal(ciphertext);
    }
}
