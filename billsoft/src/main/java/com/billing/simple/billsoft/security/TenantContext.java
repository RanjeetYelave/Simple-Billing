package com.billing.simple.billsoft.security;

import java.util.Optional;

/**
 * Authoritative thread-local tenant context for the currently executing request.
 * Enforces server-side firm isolation across all services, repositories, and calculation engines.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_FIRM_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCurrentFirmId(Long firmId) {
        if (firmId != null && firmId > 0) {
            CURRENT_FIRM_ID.set(firmId);
        } else {
            CURRENT_FIRM_ID.remove();
        }
    }

    public static Long getCurrentFirmId() {
        return CURRENT_FIRM_ID.get();
    }

    public static Optional<Long> getOptionalFirmId() {
        return Optional.ofNullable(CURRENT_FIRM_ID.get());
    }

    public static Long getRequiredFirmId() {
        Long firmId = CURRENT_FIRM_ID.get();
        if (firmId == null || firmId <= 0) {
            throw new TenantSecurityException("Active tenant context (firmId) is required for this operation.");
        }
        return firmId;
    }

    public static void clear() {
        CURRENT_FIRM_ID.remove();
    }
}
