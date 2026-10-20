package com.billing.simple.billsoft.update;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Standard JDK 21 cryptographic utilities for OTA update verification in backend.
 * Implements Ed25519 signature checks, SHA-256 digests, and ZIP magic byte validation.
 */
public final class UpdateCrypto {

    public static final String ED25519_ALGORITHM = "Ed25519";
    public static final String SHA256_ALGORITHM = "SHA-256";

    // Standard ZIP local file header signature: 0x04034b50 (little-endian: 0x50, 0x4B, 0x03, 0x04)
    public static final byte[] ZIP_MAGIC = new byte[]{0x50, 0x4b, 0x03, 0x04};

    private UpdateCrypto() {}

    public static PublicKey decodePublicKey(String base64X509) throws GeneralSecurityException {
        if (base64X509 == null || base64X509.trim().isEmpty()) {
            throw new IllegalArgumentException("Public key base64 string must not be empty");
        }
        byte[] pubBytes = Base64.getDecoder().decode(base64X509.trim());
        X509EncodedKeySpec spec = new X509EncodedKeySpec(pubBytes);
        KeyFactory kf = KeyFactory.getInstance(ED25519_ALGORITHM);
        return kf.generatePublic(spec);
    }

    public static PrivateKey decodePrivateKey(String base64PKCS8) throws GeneralSecurityException {
        if (base64PKCS8 == null || base64PKCS8.trim().isEmpty()) {
            throw new IllegalArgumentException("Private key base64 string must not be empty");
        }
        byte[] privBytes = Base64.getDecoder().decode(base64PKCS8.trim());
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privBytes);
        KeyFactory kf = KeyFactory.getInstance(ED25519_ALGORITHM);
        return kf.generatePrivate(spec);
    }

    public static String sign(byte[] data, PrivateKey privateKey) throws GeneralSecurityException {
        Signature signature = Signature.getInstance(ED25519_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(data);
        byte[] sigBytes = signature.sign();
        return Base64.getEncoder().encodeToString(sigBytes);
    }

    public static boolean verifySignature(byte[] data, String signatureBase64, PublicKey publicKey) {
        if (data == null || signatureBase64 == null || publicKey == null) {
            return false;
        }
        try {
            byte[] sigBytes = Base64.getDecoder().decode(signatureBase64.trim());
            Signature verifier = Signature.getInstance(ED25519_ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(data);
            return verifier.verify(sigBytes);
        } catch (Exception e) {
            return false;
        }
    }

    public static String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
            byte[] hash = digest.digest(data);
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String computeSha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    public static boolean isZipFile(Path file) {
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return false;
        }
        try (InputStream is = Files.newInputStream(file)) {
            byte[] header = new byte[4];
            int read = is.read(header);
            if (read < 4) return false;
            return header[0] == ZIP_MAGIC[0] &&
                   header[1] == ZIP_MAGIC[1] &&
                   header[2] == ZIP_MAGIC[2] &&
                   header[3] == ZIP_MAGIC[3];
        } catch (Exception e) {
            return false;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
