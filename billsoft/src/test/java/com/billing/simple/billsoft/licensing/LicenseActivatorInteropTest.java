package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.licensing.model.LicensePayload;
import com.billing.simple.billsoft.licensing.model.LicenseStatus;
import com.billing.simple.billsoft.licensing.model.MembershipPlan;
import com.billing.simple.billsoft.licensing.model.ValidationResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LicenseActivatorInteropTest {

    @Test
    void testEd25519SigningVerifiedByJavaClient() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair keyPair = kpg.generateKeyPair();

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

        String canonical = LicenseVerifier.buildCanonicalString(license);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(canonical.getBytes(StandardCharsets.UTF_8));
        String signatureBase64 = Base64.getEncoder().encodeToString(signer.sign());

        license.setSignature(signatureBase64);

        LicenseVerifier customVerifier = new LicenseVerifier(keyPair.getPublic());
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
        System.out.println("TEST RESULT: " + result);
        System.out.println("Canonical String:\n" + LicenseVerifier.buildCanonicalString(license));
        assertEquals(ValidationResult.VALID, result);
    }
}
