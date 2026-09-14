package com.billing.simple.billsoft.licensing.model;

/**
 * Result of license verification and runtime evaluation.
 */
public enum ValidationResult {
    VALID("License is active and valid"),
    INVALID_SIGNATURE("Cryptographic signature verification failed"),
    MACHINE_MISMATCH("License is bound to a different Machine ID"),
    PRODUCT_MISMATCH("License is for a different product"),
    SUSPENDED("License has been suspended by the developer"),
    REVOKED("License has been permanently revoked"),
    EXPIRED("Membership plan has expired"),
    CORRUPT_PAYLOAD("License file is missing or corrupted");

    private final String description;

    ValidationResult(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isValid() {
        return this == VALID;
    }
}
