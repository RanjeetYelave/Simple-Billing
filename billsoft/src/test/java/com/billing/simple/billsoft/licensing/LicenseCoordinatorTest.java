package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.licensing.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class LicenseCoordinatorTest {

    @TempDir
    File tempDir;

    private KeyPair keyPair;
    private MachineIdentity machineIdentity;
    private LicenseVerifier licenseVerifier;
    private LicenseStorage licenseStorage;
    private LicenseCoordinator coordinator;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        this.keyPair = kpg.generateKeyPair();

        File midFile = new File(tempDir, "mid.dat");
        this.machineIdentity = new MachineIdentity(midFile);

        this.licenseVerifier = new LicenseVerifier(keyPair.getPublic());
        this.licenseStorage = new LicenseStorage(tempDir);
        this.coordinator = new LicenseCoordinator(machineIdentity, licenseVerifier, licenseStorage);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (coordinator != null) {
            coordinator.shutdown();
        }
    }

    @Test
    void testLiveGitHubSync() throws Exception {
        File liveMidFile = new File(tempDir, "live_mid.dat");
        java.nio.file.Files.writeString(liveMidFile.toPath(), "RKGM-GH4X-6YX1-VVN8");
        MachineIdentity liveMid = new MachineIdentity(liveMidFile);

        LicenseVerifier liveVerifier = new LicenseVerifier();
        LicenseCoordinator liveCoordinator = new LicenseCoordinator(liveMid, liveVerifier, licenseStorage);
        liveCoordinator.syncWithRegistry(true);

        System.out.println("Live Sync Active License: " + liveCoordinator.getActiveLicense());
        System.out.println("Live Sync Validation Result: " + liveCoordinator.getCurrentValidationResult());
        assertNotNull(liveCoordinator.getActiveLicense());
        assertTrue(liveCoordinator.getCurrentValidationResult() == ValidationResult.VALID || liveCoordinator.getCurrentValidationResult() == ValidationResult.SUSPENDED);
    }

    private String signLicense(LicensePayload license) throws Exception {
        String canonical = LicenseVerifier.buildCanonicalString(license);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    @Test
    void testStartupWithoutLicenseFails() {
        assertFalse(coordinator.validateOnStartup());
        assertEquals(ValidationResult.CORRUPT_PAYLOAD, coordinator.getCurrentValidationResult());
    }

    @Test
    void testStartupWithValidLicenseSucceedsOffline() throws Exception {
        String machineId = machineIdentity.getMachineId();
        LicensePayload license = new LicensePayload(
                "LIC-001",
                machineId,
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
        licenseStorage.saveLocalLicense(license);

        assertTrue(coordinator.validateOnStartup(), "Offline startup with valid local license must succeed");
        assertEquals(ValidationResult.VALID, coordinator.getCurrentValidationResult());
    }

    @Test
    void testStartupWithExpiredLicenseSucceedsOfflineFailOpen() throws Exception {
        String machineId = machineIdentity.getMachineId();
        LicensePayload expiredLicense = new LicensePayload(
                "LIC-001",
                machineId,
                "ABC Traders",
                "RupeeCRM",
                "PRO",
                MembershipPlan.BRONZE,
                LicenseStatus.ACTIVE,
                1,
                Instant.now().minus(400, ChronoUnit.DAYS),
                Instant.now().minus(35, ChronoUnit.DAYS), // Expired 35 days ago
                null,
                null
        );
        expiredLicense.setSignature(signLicense(expiredLicense));
        licenseStorage.saveLocalLicense(expiredLicense);

        // Strict Fail-Open Rule: Offline expired must STILL permit app to run
        assertTrue(coordinator.validateOnStartup(), "Offline expired license must fail-open and allow startup");
        assertEquals(ValidationResult.VALID, coordinator.getCurrentValidationResult());
    }

    @Test
    void testStartupWithSuspendedLicenseBlocks() throws Exception {
        String machineId = machineIdentity.getMachineId();
        LicensePayload suspendedLic = new LicensePayload(
                "LIC-001",
                machineId,
                "ABC Traders",
                "RupeeCRM",
                "PRO",
                MembershipPlan.SILVER,
                LicenseStatus.SUSPENDED,
                2,
                Instant.now(),
                Instant.now().plus(3 * 365, ChronoUnit.DAYS),
                "Suspended by operator",
                null
        );
        suspendedLic.setSignature(signLicense(suspendedLic));
        licenseStorage.saveLocalLicense(suspendedLic);

        assertFalse(coordinator.validateOnStartup());
        assertEquals(ValidationResult.SUSPENDED, coordinator.getCurrentValidationResult());
    }

    @Test
    void testActivateLicenseUpdatesState() throws Exception {
        String machineId = machineIdentity.getMachineId();
        LicensePayload license = new LicensePayload(
                "LIC-002",
                machineId,
                "XYZ Stores",
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

        AtomicReference<ValidationResult> callbackResult = new AtomicReference<>();
        coordinator.setStatusChangeListener(callbackResult::set);

        ValidationResult result = coordinator.activateLicense(license);
        assertEquals(ValidationResult.VALID, result);
        assertEquals(ValidationResult.VALID, callbackResult.get());
        assertEquals(1, licenseStorage.getHighestRevision());
    }
}
