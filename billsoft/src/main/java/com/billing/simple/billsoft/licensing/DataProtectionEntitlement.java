package com.billing.simple.billsoft.licensing;

import java.time.Instant;

/**
 * Minimal decoupled entitlement interface consumed by Data Protection subsystem.
 * Avoids any direct deep coupling to core billing, licensing internals, or repositories.
 */
public interface DataProtectionEntitlement {

    /**
     * True if Data Protection add-on is enabled in the active license.
     */
    boolean isDataProtectionEnabled();

    /**
     * Expiration instant of Data Protection (null means lifetime).
     */
    Instant getDataProtectionExpiresAt();

    /**
     * True if Data Protection is enabled, license is active, and not past its expiration date.
     */
    boolean isDataProtectionActive();

    /**
     * Active License ID used for deterministic recoverable key derivation.
     */
    String getLicenseId();

    /**
     * Machine identifier for off-device backup naming.
     */
    String getMachineId();

    /**
     * Registers a periodic/opportunistic backup check hook with the primary sync cycle.
     */
    default void registerBackupCheckHook(Runnable hook) {}
}
