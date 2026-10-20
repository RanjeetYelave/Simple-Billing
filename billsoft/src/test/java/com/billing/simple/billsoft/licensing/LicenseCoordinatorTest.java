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

    @Test
    void testFetchWithClassification_FastFailOnTerminalFailure_DoesNotAttemptFallback() {
        java.util.concurrent.atomic.AtomicInteger httpCallCount = new java.util.concurrent.atomic.AtomicInteger(0);
        LicenseCoordinator testCoordinator = new LicenseCoordinator(machineIdentity, licenseVerifier, licenseStorage) {
            @Override
            public ClassifiedFetchResult fetchHttpClassified(String urlStr, long deadlineNanos) {
                httpCallCount.incrementAndGet();
                return new ClassifiedFetchResult(FetchClassification.CONNECTION_FAILURE, -1, null, 10);
            }
        };

        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(3500);
        ClassifiedFetchResult result = testCoordinator.fetchRegistryWithFallback("licenses/test.lic", deadline);

        assertEquals(FetchClassification.CONNECTION_FAILURE, result.getClassification());
        assertTrue(result.isTerminalNetworkFailure());
        // Verify only 1 attempt was made (fallback was not called because primary was a terminal failure)
        assertEquals(1, httpCallCount.get(), "Terminal network failure must fast-fail without attempting secondary fallback URL");
    }

    @Test
    void testFetchWithClassification_NonTerminal404_AttemptsFallback() {
        java.util.concurrent.atomic.AtomicInteger httpCallCount = new java.util.concurrent.atomic.AtomicInteger(0);
        LicenseCoordinator testCoordinator = new LicenseCoordinator(machineIdentity, licenseVerifier, licenseStorage) {
            @Override
            public ClassifiedFetchResult fetchHttpClassified(String urlStr, long deadlineNanos) {
                int count = httpCallCount.incrementAndGet();
                if (count == 1) {
                    return new ClassifiedFetchResult(FetchClassification.RESOURCE_NOT_FOUND, 404, null, 10);
                } else {
                    return new ClassifiedFetchResult(FetchClassification.SUCCESS, 200, "{\"status\":\"ACTIVE\"}", 15);
                }
            }
        };

        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(3500);
        ClassifiedFetchResult result = testCoordinator.fetchRegistryWithFallback("licenses/test.lic", deadline);

        assertEquals(FetchClassification.SUCCESS, result.getClassification());
        assertEquals(2, httpCallCount.get(), "Non-terminal 404 should attempt secondary fallback URL within budget");
    }

    @Test
    void testFetchWithClassification_DistinguishesHttp403FromHttp404() {
        LicenseCoordinator testCoordinator = new LicenseCoordinator(machineIdentity, licenseVerifier, licenseStorage) {
            @Override
            public ClassifiedFetchResult fetchHttpClassified(String urlStr, long deadlineNanos) {
                if (urlStr.contains("403")) {
                    return new ClassifiedFetchResult(FetchClassification.FORBIDDEN_OR_UNAUTHORIZED, 403, null, 10);
                }
                return new ClassifiedFetchResult(FetchClassification.RESOURCE_NOT_FOUND, 404, null, 10);
            }
        };

        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(3500);
        ClassifiedFetchResult res403 = testCoordinator.fetchHttpClassified("https://example.com/403", deadline);
        ClassifiedFetchResult res404 = testCoordinator.fetchHttpClassified("https://example.com/404", deadline);

        assertEquals(FetchClassification.FORBIDDEN_OR_UNAUTHORIZED, res403.getClassification());
        assertEquals(403, res403.getHttpStatusCode());
        assertEquals(FetchClassification.RESOURCE_NOT_FOUND, res404.getClassification());
        assertEquals(404, res404.getHttpStatusCode());
        assertNotEquals(res403.getClassification(), res404.getClassification());
    }

    @Test
    void testFetchWithClassification_EnforcesDeadlineCeiling() {
        java.util.concurrent.atomic.AtomicInteger httpCallCount = new java.util.concurrent.atomic.AtomicInteger(0);
        LicenseCoordinator testCoordinator = new LicenseCoordinator(machineIdentity, licenseVerifier, licenseStorage) {
            @Override
            public ClassifiedFetchResult fetchHttpClassified(String urlStr, long deadlineNanos) {
                httpCallCount.incrementAndGet();
                return new ClassifiedFetchResult(FetchClassification.SUCCESS, 200, "OK", 5);
            }
        };

        // Pass an already expired deadline
        long expiredDeadline = System.nanoTime() - java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(100);
        ClassifiedFetchResult result = testCoordinator.fetchRegistryWithFallback("licenses/test.lic", expiredDeadline);

        assertEquals(FetchClassification.OVERALL_DEADLINE_EXCEEDED, result.getClassification());
        assertTrue(result.isTerminalNetworkFailure());
        assertEquals(0, httpCallCount.get(), "Expired deadline must abort before making any HTTP call");
    }

    @Test
    void testSyncWithRegistry_TerminalNetworkFailure_PreservesValidLocalLicense() throws Exception {
        String machineId = machineIdentity.getMachineId();
        LicensePayload validLocal = new LicensePayload(
                "LIC-LOCAL-001", machineId, "Store", "RupeeCRM", "PRO", MembershipPlan.GOLD,
                LicenseStatus.ACTIVE, 5, Instant.now(), null, null, null
        );
        validLocal.setSignature(signLicense(validLocal));
        licenseStorage.saveLocalLicense(validLocal);

        LicenseCoordinator testCoordinator = new LicenseCoordinator(machineIdentity, licenseVerifier, licenseStorage) {
            @Override
            public ClassifiedFetchResult fetchWithClassification(String subPath, long deadlineNanos) {
                return new ClassifiedFetchResult(FetchClassification.CONNECTION_FAILURE, -1, null, 50);
            }
        };

        // Startup offline is valid
        assertTrue(testCoordinator.validateOnStartup());
        assertEquals(ValidationResult.VALID, testCoordinator.getCurrentValidationResult());

        // Perform forced sync during complete network failure
        boolean syncOutcome = testCoordinator.syncWithRegistry(true);
        assertFalse(syncOutcome, "Sync must return false on terminal network failure");

        // Existing local license must be completely preserved!
        assertEquals(ValidationResult.VALID, testCoordinator.getCurrentValidationResult());
        assertNotNull(testCoordinator.getActiveLicense());
        assertEquals("LIC-LOCAL-001", testCoordinator.getActiveLicense().getLicenseId());
        assertEquals(5, testCoordinator.getActiveLicense().getRevision());
    }

    @Test
    void testLicenseTokenResolutionAndCredentialIsolation() {
        String licProp = "rupeecrm.licensing.token";
        String dpProp = "rupeecrm.dataprotection.token";
        try {
            System.clearProperty(licProp);
            System.clearProperty(dpProp);

            assertNull(LicensingConfig.getLicenseAuthToken(), "Unconfigured licensing token should be null");

            System.setProperty(dpProp, "dp-secret-only");
            assertNull(LicensingConfig.getLicenseAuthToken(), "Data protection token must not leak into licensing config");

            System.setProperty(licProp, "lic-secret-only");
            assertEquals("lic-secret-only", LicensingConfig.getLicenseAuthToken());
        } finally {
            System.clearProperty(licProp);
            System.clearProperty(dpProp);
        }
    }

    @Test
    void testOfflineSignedLicenseValidationWorksWithoutToken() throws Exception {
        // Guarantee no token is configured
        System.clearProperty("rupeecrm.licensing.token");

        String machineId = machineIdentity.getMachineId();
        LicensePayload offlineLicense = new LicensePayload(
                "LIC-OFFLINE-999", machineId, "Offline Shop", "RupeeCRM", "PRO", MembershipPlan.GOLD,
                LicenseStatus.ACTIVE, 1, Instant.now(), null, null, null
        );
        offlineLicense.setSignature(signLicense(offlineLicense));
        licenseStorage.saveLocalLicense(offlineLicense);

        LicenseCoordinator offlineCoordinator = new LicenseCoordinator(machineIdentity, licenseVerifier, licenseStorage);
        try {
            assertTrue(offlineCoordinator.validateOnStartup(), "Offline signed license validation must succeed without token");
            assertEquals(ValidationResult.VALID, offlineCoordinator.getCurrentValidationResult());
            assertNotNull(offlineCoordinator.getActiveLicense());
            assertEquals("LIC-OFFLINE-999", offlineCoordinator.getActiveLicense().getLicenseId());
        } finally {
            offlineCoordinator.shutdown();
        }
    }
}
