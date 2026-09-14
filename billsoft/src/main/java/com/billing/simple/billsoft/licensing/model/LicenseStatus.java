package com.billing.simple.billsoft.licensing.model;

/**
 * Authoritative stored and published license statuses.
 * Expiry is derived dynamically at runtime (now > expiresAt) and is not a stored status.
 */
public enum LicenseStatus {
    ACTIVE,
    SUSPENDED,
    REVOKED
}
