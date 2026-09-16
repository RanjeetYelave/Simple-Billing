package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.licensing.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class LicenseVerifierTest {

    private KeyPair keyPair;
    private LicenseVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        this.keyPair = kpg.generateKeyPair();
        this.verifier = new LicenseVerifier(keyPair.getPublic());
    }

    private String signLicense(LicensePayload license) throws Exception {
        String canonical = LicenseVerifier.buildCanonicalString(license);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    @Test
    void testValidLicenseVerification() throws Exception {
        LicensePayload license = new LicensePayload(
                "LIC-00182",
                "K7XM-92QP-4B9R-XD6T",
                "ABC Traders",
                "RupeeCRM",
                "PRO",
                MembershipPlan.SILVER,
                LicenseStatus.ACTIVE,
                1,
                Instant.now(),
                Instant.now().plus(3 * 365, ChronoUnit.DAYS),
                null,
                null
        );
        license.setSignature(signLicense(license));

        ValidationResult result = verifier.verifyLicense(license, "K7XM-92QP-4B9R-XD6T");
        assertEquals(ValidationResult.VALID, result);
        assertTrue(result.isValid());
    }

    @Test
    void testTamperPlanRejection() throws Exception {
        LicensePayload license = new LicensePayload(
                "LIC-00182",
                "K7XM-92QP-4B9R-XD6T",
                "ABC Traders",
                "RupeeCRM",
                "PRO",
                MembershipPlan.BRONZE,
                LicenseStatus.ACTIVE,
                1,
                Instant.now(),
                Instant.now().plus(365, ChronoUnit.DAYS),
                null,
                null
        );
        license.setSignature(signLicense(license));

        // Attacker modifies BRONZE to GOLD
        license.setPlan(MembershipPlan.GOLD);
        license.setExpiresAt(null);

        ValidationResult result = verifier.verifyLicense(license, "K7XM-92QP-4B9R-XD6T");
        assertEquals(ValidationResult.INVALID_SIGNATURE, result);
    }

    @Test
    void testMachineMismatchRejection() throws Exception {
        LicensePayload license = new LicensePayload(
                "LIC-00182",
                "K7XM-92QP-4B9R-XD6T",
                "ABC Traders",
                "RupeeCRM",
                "PRO",
                MembershipPlan.SILVER,
                LicenseStatus.ACTIVE,
                1,
                Instant.now(),
                Instant.now().plus(3 * 365, ChronoUnit.DAYS),
                null,
                null
        );
        license.setSignature(signLicense(license));

        // Different machine tries to use this license
        ValidationResult result = verifier.verifyLicense(license, "82PQ-71LM-7K2P-9Q8X");
        assertEquals(ValidationResult.MACHINE_MISMATCH, result);
    }

    @Test
    void testSuspendedAndRevokedStatuses() throws Exception {
        LicensePayload suspendedLic = new LicensePayload(
                "LIC-00182",
                "K7XM-92QP-4B9R-XD6T",
                "ABC Traders",
                "RupeeCRM",
                "PRO",
                MembershipPlan.SILVER,
                LicenseStatus.SUSPENDED,
                2,
                Instant.now(),
                Instant.now().plus(3 * 365, ChronoUnit.DAYS),
                "Payment issue",
                null
        );
        suspendedLic.setSignature(signLicense(suspendedLic));

        ValidationResult suspendedResult = verifier.verifyLicense(suspendedLic, "K7XM-92QP-4B9R-XD6T");
        assertEquals(ValidationResult.SUSPENDED, suspendedResult);

        LicensePayload revokedLic = new LicensePayload(
                "LIC-00182",
                "K7XM-92QP-4B9R-XD6T",
                "ABC Traders",
                "RupeeCRM",
                "PRO",
                MembershipPlan.SILVER,
                LicenseStatus.REVOKED,
                3,
                Instant.now(),
                Instant.now().plus(3 * 365, ChronoUnit.DAYS),
                "Fraudulent chargeback",
                null
        );
        revokedLic.setSignature(signLicense(revokedLic));

        ValidationResult revokedResult = verifier.verifyLicense(revokedLic, "K7XM-92QP-4B9R-XD6T");
        assertEquals(ValidationResult.REVOKED, revokedResult);
    }

    @Test
    void testDerivedExpiryLogic() {
        Instant now = Instant.now();
        Instant past = now.minus(10, ChronoUnit.DAYS);
        Instant future = now.plus(10, ChronoUnit.DAYS);

        LicensePayload activeFuture = new LicensePayload("L1", "M1", "C1", "RupeeCRM", "PRO",
                MembershipPlan.BRONZE, LicenseStatus.ACTIVE, 1, past, future, null, null);
        assertFalse(verifier.isExpired(activeFuture, now));

        LicensePayload activePast = new LicensePayload("L2", "M1", "C1", "RupeeCRM", "PRO",
                MembershipPlan.BRONZE, LicenseStatus.ACTIVE, 1, past.minus(365, ChronoUnit.DAYS), past, null, null);
        assertTrue(verifier.isExpired(activePast, now));

        LicensePayload goldLifetime = new LicensePayload("L3", "M1", "C1", "RupeeCRM", "PRO",
                MembershipPlan.GOLD, LicenseStatus.ACTIVE, 1, past, null, null, null);
        assertFalse(verifier.isExpired(goldLifetime, now), "GOLD lifetime plan must never expire");
    }

    @Test
    void testMembershipExpiryArithmetic() {
        Instant issued = Instant.parse("2026-09-14T12:00:00Z");

        Instant bronzeExp = MembershipPlan.BRONZE.calculateExpiry(issued);
        assertEquals(Instant.parse("2027-09-14T12:00:00Z"), bronzeExp);

        Instant silverExp = MembershipPlan.SILVER.calculateExpiry(issued);
        assertEquals(Instant.parse("2029-09-14T12:00:00Z"), silverExp);

        Instant goldExp = MembershipPlan.GOLD.calculateExpiry(issued);
        assertNull(goldExp);
    }
}
