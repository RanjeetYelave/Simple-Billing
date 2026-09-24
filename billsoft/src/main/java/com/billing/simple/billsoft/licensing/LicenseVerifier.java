package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.licensing.model.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

/**
 * Pure Schema 3 Verifier for Ed25519 cryptographic signatures and multi-dimensional entitlement rules.
 * Obsolete Schema 1 and Schema 2 formats are strictly rejected for production security.
 */
public class LicenseVerifier {

    private static final String SCHEMA_VERSION_3 = "3";
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

    public PublicKey getMasterPublicKey() {
        return publicKey;
    }

    /**
     * Builds the deterministic canonical newline-delimited payload for Schema 3 license signing and verification.
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

        String dpEnabled = String.valueOf(Boolean.TRUE.equals(license.getDataProtectionEnabled()));
        String dpExpiresAt = (Boolean.TRUE.equals(license.getDataProtectionEnabled()) && license.getDataProtectionExpiresAt() != null)
                ? license.getDataProtectionExpiresAt().toString()
                : LIFETIME_EXPIRY_STRING;
        String statusReason = sanitize(license.getStatusReason());

        boolean emiEnabled = license.getEmi() != null && Boolean.TRUE.equals(license.getEmi().getEnabled());

        StringBuilder sb = new StringBuilder();
        sb.append(SCHEMA_VERSION_3).append("\n")
          .append(licenseId).append("\n")
          .append(machineId).append("\n")
          .append(customerName).append("\n")
          .append(product).append("\n")
          .append(edition).append("\n")
          .append(plan).append("\n")
          .append(status).append("\n")
          .append(revision).append("\n")
          .append(issuedAt).append("\n")
          .append(expiresAt).append("\n")
          .append(dpEnabled).append("\n")
          .append(dpExpiresAt).append("\n")
          .append(statusReason).append("\n")
          .append(emiEnabled ? "true" : "false");

        if (emiEnabled) {
            EmiPayload emi = license.getEmi();
            EmiPricingPayload pricing = emi.getPricing();
            EmiSchedulePayload schedule = emi.getSchedule();
            EmiStatePayload state = emi.getState();

            if (pricing != null && schedule != null && state != null) {
                sb.append("\n").append(pricing.getAgreedPrice() != null ? String.format(Locale.US, "%.2f", pricing.getAgreedPrice()) : "0.00")
                  .append("\n").append(pricing.getDownPayment() != null ? String.format(Locale.US, "%.2f", pricing.getDownPayment()) : "0.00")
                  .append("\n").append(pricing.getFinancedAmount() != null ? String.format(Locale.US, "%.2f", pricing.getFinancedAmount()) : "0.00")
                  .append("\n").append(sanitize(pricing.getInterestType()))
                  .append("\n").append(pricing.getInterestRate() != null ? String.format(Locale.US, "%.2f", pricing.getInterestRate()) : "0.00")
                  .append("\n").append(pricing.getTotalInterest() != null ? String.format(Locale.US, "%.2f", pricing.getTotalInterest()) : "0.00")
                  .append("\n").append(pricing.getTotalPayable() != null ? String.format(Locale.US, "%.2f", pricing.getTotalPayable()) : "0.00")
                  .append("\n").append(schedule.getTenureMonths() != null ? schedule.getTenureMonths().toString() : "0")
                  .append("\n").append(sanitize(schedule.getInterval()))
                  .append("\n").append(sanitize(schedule.getFirstDueDate()))
                  .append("\n").append(schedule.getGraceDays() != null ? schedule.getGraceDays().toString() : "0")
                  .append("\n").append(schedule.getInstallmentAmount() != null ? String.format(Locale.US, "%.2f", schedule.getInstallmentAmount()) : "0.00")
                  .append("\n").append(state.getPaidAmount() != null ? String.format(Locale.US, "%.2f", state.getPaidAmount()) : "0.00")
                  .append("\n").append(state.getOutstandingAmount() != null ? String.format(Locale.US, "%.2f", state.getOutstandingAmount()) : "0.00")
                  .append("\n").append(state.getCurrentInstallment() != null ? state.getCurrentInstallment().toString() : "NULL")
                  .append("\n").append(state.getNextDueDate() != null ? sanitize(state.getNextDueDate()) : "NULL")
                  .append("\n").append(state.getGraceDeadline() != null ? sanitize(state.getGraceDeadline()) : "NULL")
                  .append("\n").append(sanitize(state.getEmiStatus()))
                  .append("\n").append(sanitize(state.getAccessStatus()));
            }
        }
        return sb.toString();
    }

    /**
     * Builds the canonical string for a customer notification message.
     */
    public static String buildMessageCanonicalString(String machineId, CustomerMessage msg) {
        if (msg == null) {
            return "";
        }
        return "1\n" +
                sanitize(msg.getMessageId()) + "\n" +
                sanitize(machineId) + "\n" +
                sanitize(msg.getTitle()) + "\n" +
                sanitize(msg.getBody()) + "\n" +
                (msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : "");
    }

    /**
     * Verifies cryptographic signature and multi-dimensional status consistency.
     * Strictly enforces Schema 3.
     */
    public ValidationResult verifyLicense(LicensePayload license, String currentMachineId) {
        if (license == null) {
            return ValidationResult.CORRUPT_PAYLOAD;
        }

        // Strictly enforce Schema 3 (Reject legacy Schema 1 & Schema 2)
        if (license.getSchemaVersion() == null || license.getSchemaVersion() < 3) {
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

        // 4. Inspect Base License Status (Precedence: REVOKED > SUSPENDED)
        if (license.getStatus() == LicenseStatus.REVOKED) {
            return ValidationResult.REVOKED;
        }
        if (license.getStatus() == LicenseStatus.SUSPENDED) {
            return ValidationResult.SUSPENDED;
        }

        // 5. Inspect EMI Access Status (Schema 3)
        if (license.getEmi() != null && Boolean.TRUE.equals(license.getEmi().getEnabled())) {
            if (license.getEmi().getState() != null) {
                String accessStatus = license.getEmi().getState().getAccessStatus();
                if ("RESTRICTED".equalsIgnoreCase(accessStatus)) {
                    return ValidationResult.EMI_RESTRICTED;
                }
            }
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

    /**
     * Evaluates whether Data Protection add-on is active and unexpired.
     */
    public boolean isDataProtectionActive(LicensePayload license, Instant now) {
        if (license == null || !Boolean.TRUE.equals(license.getDataProtectionEnabled())) {
            return false;
        }
        if (license.getStatus() != LicenseStatus.ACTIVE) {
            return false;
        }
        if (license.getDataProtectionExpiresAt() == null) {
            return true; // Lifetime DP
        }
        return !now.isAfter(license.getDataProtectionExpiresAt());
    }

    public static String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\r", "").replace("\n", "").trim();
    }
}
