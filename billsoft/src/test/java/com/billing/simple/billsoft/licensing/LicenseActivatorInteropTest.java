package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.licensing.model.LicensePayload;
import com.billing.simple.billsoft.licensing.model.LicenseStatus;
import com.billing.simple.billsoft.licensing.model.MembershipPlan;
import com.billing.simple.billsoft.licensing.model.ValidationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LicenseActivatorInteropTest {

    private static KeyPair testKeyPair;
    private static LicenseVerifier customVerifier;

    @BeforeAll
    static void setup() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        testKeyPair = kpg.generateKeyPair();
        customVerifier = new LicenseVerifier(testKeyPair.getPublic());
    }

    private static void signPayload(LicensePayload license) throws Exception {
        String canonical = LicenseVerifier.buildCanonicalString(license);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(testKeyPair.getPrivate());
        signer.update(canonical.getBytes(StandardCharsets.UTF_8));
        license.setSignature(Base64.getEncoder().encodeToString(signer.sign()));
    }

    @Test
    void testEd25519SigningVerifiedByJavaClient() throws Exception {
        LicensePayload license = new LicensePayload(
                "LIC-99001",
                "K7XM-92QP-4B9R-XD6T",
                "Metro Cash & Carry",
                "RupeeCRM",
                "PRO",
                MembershipPlan.SILVER,
                LicenseStatus.ACTIVE,
                1,
                Instant.parse("2026-09-14T12:00:00Z"),
                Instant.parse("2029-09-14T23:59:59Z"),
                null,
                null
        );

        signPayload(license);
        ValidationResult result = customVerifier.verifyLicense(license, "K7XM-92QP-4B9R-XD6T");

        assertEquals(ValidationResult.VALID, result);
        assertTrue(result.isValid());
    }

    @Test
    void testUserLicensePayload() {
        LicensePayload license = new LicensePayload(
                "LIC-11962",
                "RKGM-GH4X-6YX1-VVN8",
                "Sangam Hardware",
                "RupeeCRM",
                "PRO",
                MembershipPlan.BRONZE,
                LicenseStatus.ACTIVE,
                1,
                Instant.parse("2026-09-14T10:50:16.637Z"),
                Instant.parse("2027-09-14T10:50:16.637Z"),
                null,
                "fG3RW/uh/MSeS78b2nMuGnxr9A65i62+JHu9LGtIpIDri0EeskbEafd0fdPEMhXxhNzBAlFSceXwU7KRVv2ZBA=="
        );

        LicenseVerifier verifier = new LicenseVerifier();
        ValidationResult result = verifier.verifyLicense(license, "RKGM-GH4X-6YX1-VVN8");
        assertEquals(ValidationResult.VALID, result);
    }

    @Test
    void testFullLifecycleTransitionsAndIdentityInvariance() throws Exception {
        final String canonicalMachineId = "MACH-AAAA-BBBB-CCCC";
        final String permanentLicenseId = "LIC-88888";
        final String customerName = "Apex Retailers";
        final Instant baseTime = Instant.parse("2026-09-16T00:00:00Z");

        // 1. Initial Activation: BRONZE (Rev 1)
        LicensePayload rev1 = new LicensePayload(
                permanentLicenseId,
                canonicalMachineId,
                customerName,
                "RupeeCRM",
                "PRO",
                MembershipPlan.BRONZE,
                LicenseStatus.ACTIVE,
                1,
                baseTime,
                baseTime.plusSeconds(365L * 86400),
                null,
                null
        );
        signPayload(rev1);
        assertEquals(ValidationResult.VALID, customVerifier.verifyLicense(rev1, canonicalMachineId));
        assertEquals(1, rev1.getRevision());
        assertEquals(permanentLicenseId, rev1.getLicenseId());

        // 2. Upgrade: BRONZE -> SILVER (Rev 2)
        LicensePayload rev2 = new LicensePayload(
                permanentLicenseId,
                canonicalMachineId,
                customerName,
                "RupeeCRM",
                "PRO",
                MembershipPlan.SILVER,
                LicenseStatus.ACTIVE,
                rev1.getRevision() + 1,
                baseTime,
                baseTime.plusSeconds(3L * 365 * 86400),
                null,
                null
        );
        signPayload(rev2);
        assertEquals(ValidationResult.VALID, customVerifier.verifyLicense(rev2, canonicalMachineId));
        assertEquals(2, rev2.getRevision());
        assertEquals(permanentLicenseId, rev2.getLicenseId());

        // 3. Upgrade: SILVER -> GOLD (Rev 3, Lifetime Expiry null)
        LicensePayload rev3 = new LicensePayload(
                permanentLicenseId,
                canonicalMachineId,
                customerName,
                "RupeeCRM",
                "PRO",
                MembershipPlan.GOLD,
                LicenseStatus.ACTIVE,
                rev2.getRevision() + 1,
                baseTime,
                null, // GOLD is Lifetime
                null,
                null
        );
        signPayload(rev3);
        assertEquals(ValidationResult.VALID, customVerifier.verifyLicense(rev3, canonicalMachineId));
        assertEquals(3, rev3.getRevision());
        assertEquals(permanentLicenseId, rev3.getLicenseId());
        assertNull(rev3.getExpiresAt());

        // 4. Downgrade: GOLD -> SILVER (Rev 4)
        LicensePayload rev4 = new LicensePayload(
                permanentLicenseId,
                canonicalMachineId,
                customerName,
                "RupeeCRM",
                "PRO",
                MembershipPlan.SILVER,
                LicenseStatus.ACTIVE,
                rev3.getRevision() + 1,
                baseTime,
                baseTime.plusSeconds(3L * 365 * 86400),
                null,
                null
        );
        signPayload(rev4);
        assertEquals(ValidationResult.VALID, customVerifier.verifyLicense(rev4, canonicalMachineId));
        assertEquals(4, rev4.getRevision());
        assertEquals(permanentLicenseId, rev4.getLicenseId());

        // 5. Downgrade: SILVER -> BRONZE (Rev 5)
        LicensePayload rev5 = new LicensePayload(
                permanentLicenseId,
                canonicalMachineId,
                customerName,
                "RupeeCRM",
                "PRO",
                MembershipPlan.BRONZE,
                LicenseStatus.ACTIVE,
                rev4.getRevision() + 1,
                baseTime,
                baseTime.plusSeconds(365L * 86400),
                null,
                null
        );
        signPayload(rev5);
        assertEquals(ValidationResult.VALID, customVerifier.verifyLicense(rev5, canonicalMachineId));
        assertEquals(5, rev5.getRevision());

        // 6. Direct Upgrade: BRONZE -> GOLD (Rev 6)
        LicensePayload rev6 = new LicensePayload(
                permanentLicenseId,
                canonicalMachineId,
                customerName,
                "RupeeCRM",
                "PRO",
                MembershipPlan.GOLD,
                LicenseStatus.ACTIVE,
                rev5.getRevision() + 1,
                baseTime,
                null,
                null,
                null
        );
        signPayload(rev6);
        assertEquals(ValidationResult.VALID, customVerifier.verifyLicense(rev6, canonicalMachineId));
        assertEquals(6, rev6.getRevision());

        // 7. Direct Downgrade: GOLD -> BRONZE (Rev 7)
        LicensePayload rev7 = new LicensePayload(
                permanentLicenseId,
                canonicalMachineId,
                customerName,
                "RupeeCRM",
                "PRO",
                MembershipPlan.BRONZE,
                LicenseStatus.ACTIVE,
                rev6.getRevision() + 1,
                baseTime,
                baseTime.plusSeconds(365L * 86400),
                null,
                null
        );
        signPayload(rev7);
        assertEquals(ValidationResult.VALID, customVerifier.verifyLicense(rev7, canonicalMachineId));
        assertEquals(7, rev7.getRevision());

        // 8. Renewal: BRONZE -> BRONZE (Rev 8)
        LicensePayload rev8 = new LicensePayload(
                permanentLicenseId,
                canonicalMachineId,
                customerName,
                "RupeeCRM",
                "PRO",
                MembershipPlan.BRONZE,
                LicenseStatus.ACTIVE,
                rev7.getRevision() + 1,
                baseTime,
                rev7.getExpiresAt().plusSeconds(365L * 86400),
                null,
                null
        );
        signPayload(rev8);
        assertEquals(ValidationResult.VALID, customVerifier.verifyLicense(rev8, canonicalMachineId));
        assertEquals(8, rev8.getRevision());

        // 9. Add Data Protection Add-On (Rev 9, Schema 2)
        LicensePayload rev9 = new LicensePayload(
                permanentLicenseId,
                canonicalMachineId,
                customerName,
                "RupeeCRM",
                "PRO",
                MembershipPlan.BRONZE,
                LicenseStatus.ACTIVE,
                rev8.getRevision() + 1,
                baseTime,
                rev8.getExpiresAt(),
                true,
                baseTime.plusSeconds(180L * 86400), // 6 months DP
                null,
                null
        );
        signPayload(rev9);
        assertEquals(ValidationResult.VALID, customVerifier.verifyLicense(rev9, canonicalMachineId));
        assertEquals(9, rev9.getRevision());
        assertTrue(rev9.getDataProtectionEnabled());
        assertNotNull(rev9.getDataProtectionExpiresAt());

        // 10. Data Protection Renewal (Rev 10)
        LicensePayload rev10 = new LicensePayload(
                permanentLicenseId,
                canonicalMachineId,
                customerName,
                "RupeeCRM",
                "PRO",
                MembershipPlan.BRONZE,
                LicenseStatus.ACTIVE,
                rev9.getRevision() + 1,
                baseTime,
                rev9.getExpiresAt(),
                true,
                rev9.getDataProtectionExpiresAt().plusSeconds(365L * 86400), // +1 year DP
                null,
                null
        );
        signPayload(rev10);
        assertEquals(ValidationResult.VALID, customVerifier.verifyLicense(rev10, canonicalMachineId));
        assertEquals(10, rev10.getRevision());

        // 11. Status Modification: ACTIVE -> SUSPENDED (Rev 11)
        LicensePayload rev11 = new LicensePayload(
                permanentLicenseId,
                canonicalMachineId,
                customerName,
                "RupeeCRM",
                "PRO",
                MembershipPlan.BRONZE,
                LicenseStatus.SUSPENDED,
                rev10.getRevision() + 1,
                baseTime,
                rev10.getExpiresAt(),
                true,
                rev10.getDataProtectionExpiresAt(),
                "Non-payment of invoice #102",
                null
        );
        signPayload(rev11);
        ValidationResult suspResult = customVerifier.verifyLicense(rev11, canonicalMachineId);
        assertEquals(ValidationResult.SUSPENDED, suspResult);
        assertFalse(suspResult.isValid());
        assertEquals(11, rev11.getRevision());

        // 12. Reactivation: SUSPENDED -> ACTIVE (Rev 12)
        LicensePayload rev12 = new LicensePayload(
                permanentLicenseId,
                canonicalMachineId,
                customerName,
                "RupeeCRM",
                "PRO",
                MembershipPlan.BRONZE,
                LicenseStatus.ACTIVE,
                rev11.getRevision() + 1,
                baseTime,
                rev11.getExpiresAt(),
                true,
                rev11.getDataProtectionExpiresAt(),
                null,
                null
        );
        signPayload(rev12);
        assertEquals(ValidationResult.VALID, customVerifier.verifyLicense(rev12, canonicalMachineId));
        assertEquals(12, rev12.getRevision());

        // 13. Revocation: ACTIVE -> REVOKED (Rev 13)
        LicensePayload rev13 = new LicensePayload(
                permanentLicenseId,
                canonicalMachineId,
                customerName,
                "RupeeCRM",
                "PRO",
                MembershipPlan.BRONZE,
                LicenseStatus.REVOKED,
                rev12.getRevision() + 1,
                baseTime,
                rev12.getExpiresAt(),
                true,
                rev12.getDataProtectionExpiresAt(),
                "Chargeback requested",
                null
        );
        signPayload(rev13);
        ValidationResult revokeResult = customVerifier.verifyLicense(rev13, canonicalMachineId);
        assertEquals(ValidationResult.REVOKED, revokeResult);
        assertFalse(revokeResult.isValid());
        assertEquals(13, rev13.getRevision());

        // 14. Reinstatement: REVOKED -> ACTIVE (Rev 14)
        LicensePayload rev14 = new LicensePayload(
                permanentLicenseId,
                canonicalMachineId,
                customerName,
                "RupeeCRM",
                "PRO",
                MembershipPlan.BRONZE,
                LicenseStatus.ACTIVE,
                rev13.getRevision() + 1,
                baseTime,
                rev13.getExpiresAt(),
                true,
                rev13.getDataProtectionExpiresAt(),
                null,
                null
        );
        signPayload(rev14);
        assertEquals(ValidationResult.VALID, customVerifier.verifyLicense(rev14, canonicalMachineId));
        assertEquals(14, rev14.getRevision());

        // Verify all 14 revisions retained the exact same permanent licenseId and canonicalMachineId
        List<LicensePayload> allRevisions = List.of(
                rev1, rev2, rev3, rev4, rev5, rev6, rev7, rev8, rev9, rev10, rev11, rev12, rev13, rev14
        );
        for (int i = 0; i < allRevisions.size(); i++) {
            LicensePayload item = allRevisions.get(i);
            assertEquals(permanentLicenseId, item.getLicenseId(), "licenseId must be invariant across all lifecycle events");
            assertEquals(canonicalMachineId, item.getMachineId(), "machineId must be canonical");
            assertEquals(i + 1, item.getRevision(), "revision must strictly increment R -> R+1");
        }
    }
}

