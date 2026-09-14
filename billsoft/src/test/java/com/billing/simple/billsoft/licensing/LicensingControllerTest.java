package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.controllers.LicensingController;
import com.billing.simple.billsoft.licensing.model.LicensePayload;
import com.billing.simple.billsoft.licensing.model.LicenseStatus;
import com.billing.simple.billsoft.licensing.model.MembershipPlan;
import com.billing.simple.billsoft.licensing.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LicensingControllerTest {

    @TempDir
    File tempDir;

    private KeyPair keyPair;
    private MachineIdentity machineIdentity;
    private LicenseVerifier licenseVerifier;
    private LicenseStorage licenseStorage;
    private LicenseCoordinator coordinator;
    private LicensingController controller;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        this.keyPair = kpg.generateKeyPair();

        File midFile = new File(tempDir, "mid.dat");
        this.machineIdentity = new MachineIdentity(midFile);

        this.licenseVerifier = new LicenseVerifier(keyPair.getPublic());
        this.licenseStorage = new LicenseStorage(tempDir);
        this.coordinator = new LicenseCoordinator(machineIdentity, licenseVerifier, licenseStorage);
        this.controller = new LicensingController(coordinator);
    }

    private String signLicense(LicensePayload license) throws Exception {
        String canonical = LicenseVerifier.buildCanonicalString(license);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    @Test
    void testGetStatusUnactivated() {
        ResponseEntity<Map<String, Object>> resp = controller.getStatus();
        assertNotNull(resp.getBody());
        assertEquals("CORRUPT_PAYLOAD", resp.getBody().get("validationResult"));
        assertFalse((Boolean) resp.getBody().get("isValid"));
    }

    @Test
    void testGetQrData() {
        ResponseEntity<Map<String, Object>> resp = controller.getQrData();
        assertNotNull(resp.getBody());
        assertEquals("RupeeCRM", resp.getBody().get("app"));
        assertEquals(1, resp.getBody().get("ver"));
        assertNotNull(resp.getBody().get("mid"));
    }

    @Test
    void testActivateValidLicenseViaApi() throws Exception {
        String machineId = machineIdentity.getMachineId();
        LicensePayload license = new LicensePayload(
                "LIC-099",
                machineId,
                "Apex Supermarket",
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
        license.setSignature(signLicense(license));

        ResponseEntity<Map<String, Object>> resp = controller.activate(license);
        assertNotNull(resp.getBody());
        assertTrue((Boolean) resp.getBody().get("success"));
        assertEquals("VALID", resp.getBody().get("result"));

        // Check getStatus returns valid
        ResponseEntity<Map<String, Object>> statusResp = controller.getStatus();
        assertTrue((Boolean) statusResp.getBody().get("isValid"));
        assertEquals("GOLD", statusResp.getBody().get("plan"));
        assertEquals("Apex Supermarket", statusResp.getBody().get("customerName"));
    }
}
