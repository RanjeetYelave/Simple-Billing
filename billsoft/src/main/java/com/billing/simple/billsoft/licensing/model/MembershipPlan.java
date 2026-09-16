package com.billing.simple.billsoft.licensing.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Membership tiers for RupeeCRM licensing.
 * 🥉 BRONZE: 1 Year validity
 * 🥈 SILVER: 3 Years validity
 * 🥇 GOLD: Lifetime validity (expiresAt = null)
 */
public enum MembershipPlan {
    BRONZE("Bronze", 1),
    SILVER("Silver", 3),
    GOLD("Gold", 0);

    private final String displayName;
    private final int validityYears;

    MembershipPlan(String displayName, int validityYears) {
        this.displayName = displayName;
        this.validityYears = validityYears;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getValidityYears() {
        return validityYears;
    }

    public boolean isLifetime() {
        return this == GOLD;
    }

    /**
     * Calculates the expiration instant based on the issue timestamp.
     * For GOLD, returns null.
     */
    public Instant calculateExpiry(Instant issuedAt) {
        if (isLifetime() || issuedAt == null) {
            return null;
        }
        ZonedDateTime zdt = issuedAt.atZone(ZoneOffset.UTC);
        return zdt.plusYears(validityYears).toInstant();
    }
}
