package com.billing.simple.billsoft.dataprotection;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Handles client-side GZIP compression, AES-256-GCM encryption, and
 * deterministic recoverable key derivation for off-device backups.
 * Container Format:
 * [0..3]   Magic Bytes : 0x52 0x43 0x42 0x50 ("RCBP")
 * [4]      Version     : 0x01
 * [5..16]  IV (12 bytes)
 * [17..N]  AES-256-GCM Ciphertext (includes 16-byte authentication tag)
 */
public class DataProtectionCrypto {

    public static final byte[] MAGIC_BYTES = new byte[]{'R', 'C', 'B', 'P'};
    public static final byte CONTAINER_VERSION_1 = 0x01;
    public static final int GCM_IV_LENGTH = 12;
    public static final int GCM_TAG_LENGTH_BITS = 128;

    // Tunable PBKDF2 parameters for deterministic recoverable key derivation
    public static final int PBKDF2_ITERATIONS = 10000;
    public static final int KEY_LENGTH_BITS = 256;
    private static final byte[] STATIC_APPLICATION_SALT = "RupeeCRM.DataProtection.Vault.Salt.2026".getBytes(StandardCharsets.UTF_8);

    private final SecureRandom secureRandom;

    public DataProtectionCrypto() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Derives a deterministic 256-bit AES key from the customer's License ID and application salt.
     * Note: This is a deterministic recoverable key derivation mechanism for at-rest vault obfuscation/privacy.
     */
    public SecretKey deriveKeyFromLicenseId(String licenseId) {
        if (licenseId == null || licenseId.isBlank()) {
            throw new IllegalArgumentException("License ID cannot be empty for key derivation");
        }
        try {
            PBEKeySpec spec = new PBEKeySpec(licenseId.trim().toCharArray(), STATIC_APPLICATION_SALT, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES-256 key: " + e.getMessage(), e);
        }
    }

    /**
     * Compresses plaintext using GZIP (Level 9).
     */
    public byte[] compressGzip(byte[] rawData) throws IOException {
        if (rawData == null || rawData.length == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(rawData);
            gzos.finish();
        }
        return baos.toByteArray();
    }

    /**
     * Decompresses GZIP byte stream.
     */
    public byte[] decompressGzip(byte[] compressedData) throws IOException {
        if (compressedData == null || compressedData.length == 0) {
            return new byte[0];
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        try (GZIPInputStream gzis = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = gzis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    /**
     * Packages raw JSON into compressed, AES-256-GCM encrypted "RCBP" container.
     */
    public byte[] encryptBackup(byte[] rawJsonBytes, SecretKey secretKey) throws Exception {
        if (rawJsonBytes == null || rawJsonBytes.length == 0) {
            throw new IllegalArgumentException("Backup data cannot be empty");
        }
        byte[] gzipped = compressGzip(rawJsonBytes);

        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
        byte[] ciphertext = cipher.doFinal(gzipped);

        ByteArrayOutputStream out = new ByteArrayOutputStream(MAGIC_BYTES.length + 1 + GCM_IV_LENGTH + ciphertext.length);
        out.write(MAGIC_BYTES);
        out.write(CONTAINER_VERSION_1);
        out.write(iv);
        out.write(ciphertext);
        return out.toByteArray();
    }

    /**
     * Decrypts "RCBP" container and decompresses into original raw JSON bytes.
     */
    public byte[] decryptBackup(byte[] containerBytes, SecretKey secretKey) throws Exception {
        if (containerBytes == null || containerBytes.length < MAGIC_BYTES.length + 1 + GCM_IV_LENGTH + 16) {
            throw new IllegalArgumentException("Corrupt or incomplete container bytes");
        }

        // 1. Verify Magic Bytes
        if (containerBytes[0] != MAGIC_BYTES[0] || containerBytes[1] != MAGIC_BYTES[1] ||
            containerBytes[2] != MAGIC_BYTES[2] || containerBytes[3] != MAGIC_BYTES[3]) {
            throw new IllegalArgumentException("Invalid container magic header");
        }

        // 2. Verify Version
        byte version = containerBytes[4];
        if (version != CONTAINER_VERSION_1) {
            throw new IllegalArgumentException("Unsupported container version: " + version);
        }

        // 3. Extract IV
        byte[] iv = Arrays.copyOfRange(containerBytes, 5, 5 + GCM_IV_LENGTH);

        // 4. Extract Ciphertext & Tag
        byte[] ciphertext = Arrays.copyOfRange(containerBytes, 5 + GCM_IV_LENGTH, containerBytes.length);

        // 5. Decrypt GCM
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        byte[] gzipped = cipher.doFinal(ciphertext);

        // 6. Decompress GZIP
        return decompressGzip(gzipped);
    }
}
