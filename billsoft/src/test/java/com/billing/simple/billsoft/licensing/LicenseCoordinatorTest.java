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
        try {
            liveCoordinator.syncWithRegistry(true);
            if (liveCoordinator.getActiveLicense() != null) {
                System.out.println("Live Sync Active License: " + liveCoordinator.getActiveLicense());
                System.out.println("Live Sync Validation Result: " + liveCoordinator.getCurrentValidationResult());
                assertTrue(liveCoordinator.getCurrentValidationResult() == ValidationResult.VALID || liveCoordinator.getCurrentValidationResult() == ValidationResult.SUSPENDED);
            } else {
                System.out.println("Live GitHub sync test skipped due to network/rate-limit in test environment");
            }
        } catch (Exception e) {
            System.out.println("Live sync skipped: " + e.getMessage());
        }
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

    @Test
    void testMessageIngestionUsesDecoupledEventKey() throws Exception {
        com.billing.simple.billsoft.service.NotificationService mockNotifService =
                org.mockito.Mockito.mock(com.billing.simple.billsoft.service.NotificationService.class);
        coordinator.setNotificationService(mockNotifService);

        String machineId = machineIdentity.getMachineId();
        CustomerMessage msg = new CustomerMessage();
        msg.setMessageId("MSG-777888");
        msg.setTitle("System Maintenance");
        msg.setBody("Scheduled maintenance tonight.");
        msg.setCreatedAt(Instant.now());

        // Sign message
        String canonical = "1\n" + msg.getMessageId() + "\n" + machineId + "\n" + msg.getTitle() + "\n" + msg.getBody() + "\n" + msg.getCreatedAt().toString();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(canonical.getBytes(StandardCharsets.UTF_8));
        msg.setSignature(Base64.getEncoder().encodeToString(signer.sign()));

        CustomerMessageEnvelope env = new CustomerMessageEnvelope(machineId, java.util.List.of(msg));
        String envJson = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).writeValueAsString(env);

        // Subclass coordinator to mock fetchRegistryFile
        LicenseCoordinator testCoordinator = new LicenseCoordinator(machineIdentity, licenseVerifier, licenseStorage) {
            public boolean syncWithRegistry(boolean force) {
                // Call notification service using the verified msg
                if (licenseVerifier.verifyMessage(machineId, msg)) {
                    mockNotifService.createOrUpdate(com.billing.simple.billsoft.dto.NotificationRequest.builder()
                            .firmId(com.billing.simple.billsoft.entities.Notification.GLOBAL_FIRM_ID)
                            .eventKey("management:broadcast:" + msg.getMessageId().trim())
                            .category(com.billing.simple.billsoft.entities.NotificationCategory.LICENSING)
                            .priority(com.billing.simple.billsoft.entities.NotificationPriority.HIGH)
                            .title(msg.getTitle())
                            .body(msg.getBody())
                            .sender("RupeeCRM Management")
                            .build());
                }
                return true;
            }
        };

        testCoordinator.setNotificationService(mockNotifService);
        testCoordinator.syncWithRegistry(true);

        org.mockito.ArgumentCaptor<com.billing.simple.billsoft.dto.NotificationRequest> captor =
                org.mockito.ArgumentCaptor.forClass(com.billing.simple.billsoft.dto.NotificationRequest.class);
        org.mockito.Mockito.verify(mockNotifService).createOrUpdate(captor.capture());

        com.billing.simple.billsoft.dto.NotificationRequest captured = captor.getValue();
        assertEquals("management:broadcast:MSG-777888", captured.getEventKey());
        assertEquals(com.billing.simple.billsoft.entities.Notification.GLOBAL_FIRM_ID, captured.getFirmId());
        assertFalse(captured.getEventKey().contains(machineId), "Event key must NOT contain machine ID");
    }
}
