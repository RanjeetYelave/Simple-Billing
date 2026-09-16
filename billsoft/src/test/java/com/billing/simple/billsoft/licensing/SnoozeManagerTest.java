package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.licensing.model.LicensePayload;
import com.billing.simple.billsoft.licensing.model.LicenseStatus;
import com.billing.simple.billsoft.licensing.model.MembershipPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class SnoozeManagerTest {

    @TempDir
    File tempDir;

    private SnoozeManager snoozeManager;

    @BeforeEach
    public void setUp() {
        snoozeManager = new SnoozeManager(tempDir);
    }

    @Test
    public void testLicenseSnoozeCappedToExpiry() {
        // License expires in 3 days
        Instant expiresAt = Instant.now().plus(Duration.ofDays(3));
        LicensePayload license = new LicensePayload("LIC-100", "MID-100", "Customer", "RupeeCRM",
                "Desktop", MembershipPlan.SILVER, LicenseStatus.ACTIVE, 1,
                Instant.now().minus(Duration.ofDays(10)), expiresAt, true, expiresAt, "", "sig");

        // Attempting to snooze for 7 days must be capped to 3 days (expiresAt)
        Instant snoozedUntil = snoozeManager.snoozeLicense("7d", license);

        assertNotNull(snoozedUntil);
        assertEquals(expiresAt.toEpochMilli() / 1000, snoozedUntil.toEpochMilli() / 1000,
                "License snooze must not exceed remaining license term");
        assertTrue(snoozeManager.isLicenseSnoozed(1));
    }

    @Test
    public void testDataProtectionSnoozeUnbounded() {
        // Data Protection expires in 2 days
        Instant dpExpiresAt = Instant.now().plus(Duration.ofDays(2));
        LicensePayload license = new LicensePayload("LIC-100", "MID-100", "Customer", "RupeeCRM",
                "Desktop", MembershipPlan.SILVER, LicenseStatus.ACTIVE, 1,
                Instant.now().minus(Duration.ofDays(10)), Instant.now().plus(Duration.ofDays(30)),
                true, dpExpiresAt, "", "sig");

        // DP snooze for 30 days is ALLOWED to exceed DP expiry
        Instant snoozedUntil = snoozeManager.snoozeDataProtection("30d", license);

        assertNotNull(snoozedUntil);
        assertTrue(snoozedUntil.isAfter(dpExpiresAt), "Data Protection snooze can freely exceed remaining DP term");
        assertTrue(snoozeManager.isDataProtectionSnoozed(1));

        // Test permanent snooze
        snoozeManager.snoozeDataProtection("permanent", license);
        assertTrue(snoozeManager.isDataProtectionSnoozed(1));
        assertTrue(snoozeManager.getCurrentState().isDpPermanentlySnoozed());
    }

    @Test
    public void testSnoozeResetOnRevisionBump() {
        LicensePayload rev1 = new LicensePayload("LIC-100", "MID-100", "Customer", "RupeeCRM",
                "Desktop", MembershipPlan.SILVER, LicenseStatus.ACTIVE, 1,
                Instant.now(), Instant.now().plus(Duration.ofDays(20)), true, Instant.now().plus(Duration.ofDays(20)), "", "sig");

        snoozeManager.snoozeLicense("1d", rev1);
        snoozeManager.snoozeDataProtection("permanent", rev1);

        assertTrue(snoozeManager.isLicenseSnoozed(1));
        assertTrue(snoozeManager.isDataProtectionSnoozed(1));

        // License is renewed / modified to Revision 2 (R' > R)
        snoozeManager.checkAndResetOnRevisionBump(2);

        assertFalse(snoozeManager.isLicenseSnoozed(2), "License snooze must reset on revision increment");
        assertFalse(snoozeManager.isDataProtectionSnoozed(2), "Data Protection snooze must reset on revision increment");
    }
}
