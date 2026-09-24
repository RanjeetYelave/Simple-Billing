package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.licensing.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        this.keyPair = kpg.generateKeyPair();
        this.verifier = new LicenseVerifier(keyPair.getPublic());
    }

    private String signLicense(LicensePayload license) throws Exception {
        if (license.getSchemaVersion() == null) {
            license.setSchemaVersion(3);
        }
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
        license.setSchemaVersion(3);
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
        license.setSchemaVersion(3);
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
        license.setSchemaVersion(3);
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
                Instant.now().plus(365, ChronoUnit.DAYS),
                null,
                null
        );
        suspendedLic.setSchemaVersion(3);
        suspendedLic.setSignature(signLicense(suspendedLic));

        assertEquals(ValidationResult.SUSPENDED, verifier.verifyLicense(suspendedLic, "K7XM-92QP-4B9R-XD6T"));

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
                Instant.now().plus(365, ChronoUnit.DAYS),
                null,
                null
        );
        revokedLic.setSchemaVersion(3);
        revokedLic.setSignature(signLicense(revokedLic));

        assertEquals(ValidationResult.REVOKED, verifier.verifyLicense(revokedLic, "K7XM-92QP-4B9R-XD6T"));
    }

    @Test
    void testExpirationCheck() {
        Instant now = Instant.now();
        LicensePayload validLic = new LicensePayload(
                "LIC-1", "MID", "Cust", "RupeeCRM", "PRO",
                MembershipPlan.BRONZE, LicenseStatus.ACTIVE, 1,
                now.minus(10, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS), null, null
        );
        assertFalse(verifier.isExpired(validLic, now));

        LicensePayload expiredLic = new LicensePayload(
                "LIC-2", "MID", "Cust", "RupeeCRM", "PRO",
                MembershipPlan.BRONZE, LicenseStatus.ACTIVE, 1,
                now.minus(20, ChronoUnit.DAYS), now.minus(5, ChronoUnit.DAYS), null, null
        );
        assertTrue(verifier.isExpired(expiredLic, now));

        LicensePayload lifetimeLic = new LicensePayload(
                "LIC-3", "MID", "Cust", "RupeeCRM", "PRO",
                MembershipPlan.GOLD, LicenseStatus.ACTIVE, 1,
                now.minus(20, ChronoUnit.DAYS), null, null, null
        );
        assertFalse(verifier.isExpired(lifetimeLic, now));
    }

    @Test
    void testDataProtectionEntitlement() {
        Instant now = Instant.now();
        LicensePayload licWithDp = new LicensePayload(
                "LIC-1", "MID", "Cust", "RupeeCRM", "PRO",
                MembershipPlan.BRONZE, LicenseStatus.ACTIVE, 1,
                now.minus(10, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS),
                true, now.plus(30, ChronoUnit.DAYS), null, null
        );
        assertTrue(verifier.isDataProtectionActive(licWithDp, now));

        LicensePayload licWithoutDp = new LicensePayload(
                "LIC-2", "MID", "Cust", "RupeeCRM", "PRO",
                MembershipPlan.BRONZE, LicenseStatus.ACTIVE, 1,
                now.minus(10, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS),
                false, null, null, null
        );
        assertFalse(verifier.isDataProtectionActive(licWithoutDp, now));

        LicensePayload licExpiredDp = new LicensePayload(
                "LIC-3", "MID", "Cust", "RupeeCRM", "PRO",
                MembershipPlan.BRONZE, LicenseStatus.ACTIVE, 1,
                now.minus(10, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS),
                true, now.minus(2, ChronoUnit.DAYS), null, null
        );
        assertFalse(verifier.isDataProtectionActive(licExpiredDp, now));
    }

    @Test
    void testPlanExpiryCalculation() {
        Instant issued = Instant.parse("2026-09-14T12:00:00Z");

        Instant bronzeExp = MembershipPlan.BRONZE.calculateExpiry(issued);
        assertEquals(Instant.parse("2027-09-14T12:00:00Z"), bronzeExp);

        Instant silverExp = MembershipPlan.SILVER.calculateExpiry(issued);
        assertEquals(Instant.parse("2029-09-14T12:00:00Z"), silverExp);

        Instant goldExp = MembershipPlan.GOLD.calculateExpiry(issued);
        assertNull(goldExp);
    }

    @Test
    void testSchema3EmiEnabledVerification() throws Exception {
        EmiPricingPayload pricing = new EmiPricingPayload();
        pricing.setInterestType("FLAT_RATE");
        pricing.setInterestRate(new java.math.BigDecimal("12.00"));
        pricing.setAgreedPrice(new java.math.BigDecimal("10000.00"));
        pricing.setDownPayment(new java.math.BigDecimal("2000.00"));
        pricing.setFinancedAmount(new java.math.BigDecimal("8000.00"));
        pricing.setTotalInterest(new java.math.BigDecimal("800.00"));
        pricing.setTotalPayable(new java.math.BigDecimal("8800.00"));

        EmiSchedulePayload schedule = new EmiSchedulePayload();
        schedule.setTenureMonths(8);
        schedule.setInterval("MONTHLY");
        schedule.setFirstDueDate("2026-10-01");
        schedule.setGraceDays(5);
        schedule.setInstallmentAmount(new java.math.BigDecimal("1100.00"));

        EmiStatePayload emiState = new EmiStatePayload();
        emiState.setPaidAmount(new java.math.BigDecimal("0.00"));
        emiState.setOutstandingAmount(new java.math.BigDecimal("8800.00"));
        emiState.setCurrentInstallment(1);
        emiState.setNextDueDate("2026-10-01");
        emiState.setGraceDeadline("2026-10-06");
        emiState.setEmiStatus("ACTIVE");
        emiState.setAccessStatus("NORMAL");

        EmiPayload emi = new EmiPayload();
        emi.setEnabled(true);
        emi.setPricing(pricing);
        emi.setSchedule(schedule);
        emi.setState(emiState);

        LicensePayload license = new LicensePayload(
                "LIC-SCH3-001",
                "K7XM-92QP-4B9R-XD6T",
                "ABC Traders",
                "RupeeCRM",
                "PRO",
                MembershipPlan.SILVER,
                LicenseStatus.ACTIVE,
                1,
                Instant.now(),
                Instant.now().plus(365, ChronoUnit.DAYS),
                null,
                null
        );
        license.setSchemaVersion(3);
        license.setEmi(emi);
        license.setSignature(signLicense(license));

        ValidationResult result = verifier.verifyLicense(license, "K7XM-92QP-4B9R-XD6T");
        assertEquals(ValidationResult.VALID, result);
        assertTrue(result.isValid());
    }

    @Test
    void testSchema3EmiRestrictedStatus() throws Exception {
        EmiPricingPayload pricing = new EmiPricingPayload();
        pricing.setInterestType("NO_COST");
        pricing.setInterestRate(new java.math.BigDecimal("0.00"));
        pricing.setAgreedPrice(new java.math.BigDecimal("10000.00"));
        pricing.setDownPayment(new java.math.BigDecimal("2000.00"));
        pricing.setFinancedAmount(new java.math.BigDecimal("8000.00"));
        pricing.setTotalInterest(new java.math.BigDecimal("0.00"));
        pricing.setTotalPayable(new java.math.BigDecimal("8000.00"));

        EmiSchedulePayload schedule = new EmiSchedulePayload();
        schedule.setTenureMonths(4);
        schedule.setInterval("MONTHLY");
        schedule.setFirstDueDate("2026-08-01");
        schedule.setGraceDays(5);
        schedule.setInstallmentAmount(new java.math.BigDecimal("2000.00"));

        EmiStatePayload emiState = new EmiStatePayload();
        emiState.setPaidAmount(new java.math.BigDecimal("2000.00"));
        emiState.setOutstandingAmount(new java.math.BigDecimal("6000.00"));
        emiState.setCurrentInstallment(2);
        emiState.setNextDueDate("2026-08-01");
        emiState.setGraceDeadline("2026-08-06");
        emiState.setEmiStatus("DEFAULTED");
        emiState.setAccessStatus("RESTRICTED");

        EmiPayload emi = new EmiPayload();
        emi.setEnabled(true);
        emi.setPricing(pricing);
        emi.setSchedule(schedule);
        emi.setState(emiState);

        LicensePayload license = new LicensePayload(
                "LIC-SCH3-RESTRICTED",
                "K7XM-92QP-4B9R-XD6T",
                "ABC Traders",
                "RupeeCRM",
                "PRO",
                MembershipPlan.SILVER,
                LicenseStatus.ACTIVE,
                2,
                Instant.now(),
                Instant.now().plus(365, ChronoUnit.DAYS),
                null,
                null
        );
        license.setSchemaVersion(3);
        license.setEmi(emi);
        license.setSignature(signLicense(license));

        ValidationResult result = verifier.verifyLicense(license, "K7XM-92QP-4B9R-XD6T");
        assertEquals(ValidationResult.EMI_RESTRICTED, result);
        assertFalse(result.isValid());
    }

    @Test
    void testSchema3TamperedEmiStateRejection() throws Exception {
        EmiPricingPayload pricing = new EmiPricingPayload();
        pricing.setInterestType("NO_COST");
        pricing.setInterestRate(new java.math.BigDecimal("0.00"));
        pricing.setAgreedPrice(new java.math.BigDecimal("10000.00"));
        pricing.setDownPayment(new java.math.BigDecimal("2000.00"));
        pricing.setFinancedAmount(new java.math.BigDecimal("8000.00"));
        pricing.setTotalInterest(new java.math.BigDecimal("0.00"));
        pricing.setTotalPayable(new java.math.BigDecimal("8000.00"));

        EmiSchedulePayload schedule = new EmiSchedulePayload();
        schedule.setTenureMonths(4);
        schedule.setInterval("MONTHLY");
        schedule.setFirstDueDate("2026-08-01");
        schedule.setGraceDays(5);
        schedule.setInstallmentAmount(new java.math.BigDecimal("2000.00"));

        EmiStatePayload emiState = new EmiStatePayload();
        emiState.setPaidAmount(new java.math.BigDecimal("2000.00"));
        emiState.setOutstandingAmount(new java.math.BigDecimal("6000.00"));
        emiState.setCurrentInstallment(2);
        emiState.setNextDueDate("2026-08-01");
        emiState.setGraceDeadline("2026-08-06");
        emiState.setEmiStatus("DEFAULTED");
        emiState.setAccessStatus("RESTRICTED");

        EmiPayload emi = new EmiPayload();
        emi.setEnabled(true);
        emi.setPricing(pricing);
        emi.setSchedule(schedule);
        emi.setState(emiState);

        LicensePayload license = new LicensePayload(
                "LIC-SCH3-TAMPER",
                "K7XM-92QP-4B9R-XD6T",
                "ABC Traders",
                "RupeeCRM",
                "PRO",
                MembershipPlan.SILVER,
                LicenseStatus.ACTIVE,
                2,
                Instant.now(),
                Instant.now().plus(365, ChronoUnit.DAYS),
                null,
                null
        );
        license.setSchemaVersion(3);
        license.setEmi(emi);
        license.setSignature(signLicense(license));

        // Customer tampers with accessStatus in local JSON file to NORMAL
        emiState.setAccessStatus("NORMAL");

        ValidationResult result = verifier.verifyLicense(license, "K7XM-92QP-4B9R-XD6T");
        assertEquals(ValidationResult.INVALID_SIGNATURE, result);
    }

    @Test
    void testSchema3NonEmiVerification() throws Exception {
        EmiPayload emi = new EmiPayload();
        emi.setEnabled(false);

        LicensePayload license = new LicensePayload(
                "LIC-SCH3-NON-EMI",
                "K7XM-92QP-4B9R-XD6T",
                "ABC Traders",
                "RupeeCRM",
                "PRO",
                MembershipPlan.GOLD,
                LicenseStatus.ACTIVE,
                1,
                Instant.now(),
                null,
                null,
                null
        );
        license.setSchemaVersion(3);
        license.setEmi(emi);
        license.setSignature(signLicense(license));

        ValidationResult result = verifier.verifyLicense(license, "K7XM-92QP-4B9R-XD6T");
        assertEquals(ValidationResult.VALID, result);
        assertTrue(result.isValid());
    }

    @Test
    void testRejectSchema1LegacyLicense() throws Exception {
        LicensePayload license = new LicensePayload(
                "LIC-LEGACY-1",
                "K7XM-92QP-4B9R-XD6T",
                "Legacy Customer",
                "RupeeCRM",
                "PRO",
                MembershipPlan.SILVER,
                LicenseStatus.ACTIVE,
                1,
                Instant.now(),
                Instant.now().plus(365, ChronoUnit.DAYS),
                null,
                "dummySig"
        );
        license.setSchemaVersion(1);

        ValidationResult result = verifier.verifyLicense(license, "K7XM-92QP-4B9R-XD6T");
        assertEquals(ValidationResult.CORRUPT_PAYLOAD, result);
        assertFalse(result.isValid());
    }

    @Test
    void testRejectSchema2LegacyLicense() throws Exception {
        LicensePayload license = new LicensePayload(
                "LIC-LEGACY-2",
                "K7XM-92QP-4B9R-XD6T",
                "Legacy Customer",
                "RupeeCRM",
                "PRO",
                MembershipPlan.SILVER,
                LicenseStatus.ACTIVE,
                1,
                Instant.now(),
                Instant.now().plus(365, ChronoUnit.DAYS),
                true,
                Instant.now().plus(365, ChronoUnit.DAYS),
                null,
                "dummySig"
        );
        license.setSchemaVersion(2);

        ValidationResult result = verifier.verifyLicense(license, "K7XM-92QP-4B9R-XD6T");
        assertEquals(ValidationResult.CORRUPT_PAYLOAD, result);
        assertFalse(result.isValid());
    }
}
