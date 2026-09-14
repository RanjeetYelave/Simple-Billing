package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.licensing.model.CustomerMessage;
import com.billing.simple.billsoft.licensing.model.LicensePayload;
import com.billing.simple.billsoft.licensing.model.LicenseStatus;
import com.billing.simple.billsoft.licensing.model.MembershipPlan;
import com.billing.simple.billsoft.licensing.model.ValidationResult;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * Verifier for cryptographic signatures and license domain rules.
 */
public class LicenseVerifier {

    private static final String SCHEMA_VERSION = "1";
    private static final String LIFETIME_EXPIRY_STRING = "LIFETIME";

    private final PublicKey publicKey;

    public LicenseVerifier() {
        this(LicensingConfig.MASTER_PUBLIC_KEY_X509_BASE64);
    }

    public LicenseVerifier(String publicKeyX509Base64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyX509Base64);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            this.publicKey = kf.generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Ed25519 Public Key: " + e.getMessage(), e);
        }
    }

    public LicenseVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    /**
     * Builds the deterministic canonical newline-delimited payload for license signing and verification.
     */
    public static String buildCanonicalString(LicensePayload license) {
        if (license == null) {
            return "";
        }
        String licenseId = sanitize(license.getLicenseId());
        String machineId = sanitize(license.getMachineId());
        String customerName = sanitize(license.getCustomerName());
        String product = sanitize(license.getProduct());
        String edition = sanitize(license.getEdition());
        String plan = license.getPlan() != null ? license.getPlan().name() : "";
        String status = license.getStatus() != null ? license.getStatus().name() : "";
        String revision = String.valueOf(license.getRevision());
        String issuedAt = license.getIssuedAt() != null ? license.getIssuedAt().toString() : "";
        String expiresAt = license.getExpiresAt() != null ? license.getExpiresAt().toString() : LIFETIME_EXPIRY_STRING;

        return SCHEMA_VERSION + "\n" +
                licenseId + "\n" +
                machineId + "\n" +
                customerName + "\n" +
                product + "\n" +
                edition + "\n" +
                plan + "\n" +
                status + "\n" +
                revision + "\n" +
                issuedAt + "\n" +
                expiresAt;
    }

    /**
     * Builds the canonical string for a customer notification message.
     */
    public static String buildMessageCanonicalString(String machineId, CustomerMessage msg) {
        if (msg == null) {
            return "";
        }
        return SCHEMA_VERSION + "\n" +
                sanitize(msg.getMessageId()) + "\n" +
                sanitize(machineId) + "\n" +
                sanitize(msg.getTitle()) + "\n" +
                sanitize(msg.getBody()) + "\n" +
                (msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : "");
    }

    /**
     * Verifies cryptographic signature and basic schema consistency.
     */
    public ValidationResult verifyLicense(LicensePayload license, String currentMachineId) {
        if (license == null) {
            return ValidationResult.CORRUPT_PAYLOAD;
        }

        // 1. Verify Product
        if (!LicensingConfig.PRODUCT_NAME.equalsIgnoreCase(license.getProduct())) {
            return ValidationResult.PRODUCT_MISMATCH;
        }

        // 2. Verify Machine ID
        if (currentMachineId != null && !currentMachineId.equalsIgnoreCase(license.getMachineId())) {
            return ValidationResult.MACHINE_MISMATCH;
        }

        // 3. Verify Signature
        if (license.getSignature() == null || license.getSignature().isBlank()) {
            return ValidationResult.INVALID_SIGNATURE;
        }

        try {
            String canonical = buildCanonicalString(license);
            byte[] signatureBytes = Base64.getDecoder().decode(license.getSignature().trim());

            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(canonical.getBytes(StandardCharsets.UTF_8));

            if (!sig.verify(signatureBytes)) {
                return ValidationResult.INVALID_SIGNATURE;
            }
        } catch (Exception e) {
            return ValidationResult.INVALID_SIGNATURE;
        }

        // 4. Inspect Status
        if (license.getStatus() == LicenseStatus.SUSPENDED) {
            return ValidationResult.SUSPENDED;
        }
        if (license.getStatus() == LicenseStatus.REVOKED) {
            return ValidationResult.REVOKED;
        }

        return ValidationResult.VALID;
    }

    /**
     * Verifies a customer message signature.
     */
    public boolean verifyMessage(String machineId, CustomerMessage message) {
        if (message == null || message.getSignature() == null) {
            return false;
        }
        try {
            String canonical = buildMessageCanonicalString(machineId, message);
            byte[] signatureBytes = Base64.getDecoder().decode(message.getSignature().trim());

            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(canonical.getBytes(StandardCharsets.UTF_8));
            return sig.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Evaluates whether an active license has passed its expiration instant.
     */
    public boolean isExpired(LicensePayload license, Instant now) {
        if (license == null || license.getStatus() != LicenseStatus.ACTIVE) {
            return false;
        }
        if (license.getPlan() == MembershipPlan.GOLD || license.getExpiresAt() == null) {
            return false;
        }
        return now.isAfter(license.getExpiresAt());
    }

    private static String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\n", " ").replace("\r", "").trim();
    }
}
