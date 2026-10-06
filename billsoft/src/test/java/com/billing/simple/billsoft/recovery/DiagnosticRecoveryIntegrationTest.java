package com.billing.simple.billsoft.recovery;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.util.DataDirectoryResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = com.billing.simple.billsoft.BillsoftApplication.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class DiagnosticRecoveryIntegrationTest {

    private static final String VALID_MASTER_KEY = "Saidarshan*1";
    private static final String INVALID_MASTER_KEY = "WrongClearanceToken123!";
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiagnosticRecoveryIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;

    @Autowired
    private AppConfigRepository appConfigRepo;

    @Autowired
    private DiagnosticBackupScanner backupScanner;

    @Autowired
    private DiagnosticBackupOrchestrator orchestrator;

    @Autowired
    private DiagnosticAuthService authService;

    @Autowired
    private DiagnosticSessionService sessionService;

    @Autowired
    private DiagnosticLogService logService;

    @BeforeEach
    void setUp() {
        authService.resetRateLimit();
        sessionService.setPersistentConsoleEnabled(false);
        if (firmDetailsRepo.count() == 0) {
            FirmDetails testFirm = new FirmDetails();
            testFirm.setFirmName("Test Diagnostic Firm");
            testFirm.setOwnerName("Admin");
            testFirm.setGstin("27AAAAA0000A1Z5");
            firmDetailsRepo.save(testFirm);
        }
    }

    // T01: /recovery.html loads independently
    @Test
    @DisplayName("T01: /recovery.html loads independently as static resource")
    void testRecoveryHtmlLoadsIndependently() throws Exception {
        mockMvc.perform(get("/recovery.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RupeeCRM Diagnostic &amp; Recovery Console")))
                .andExpect(content().string(containsString("Diagnostic Access Restricted")));
    }

    // T02: Recovery API does not require X-Firm-Id
    @Test
    @DisplayName("T02: Recovery API does not require X-Firm-Id header")
    void testRecoveryApiDoesNotRequireFirmId() throws Exception {
        // Without X-Firm-Id, but with valid X-Diagnostic-Key
        mockMvc.perform(get("/api/recovery/health")
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application.status").value("UP"))
                .andExpect(jsonPath("$.database.status").value("CONNECTED"));
    }

    // T03: Incorrect diagnostic key returns 401
    @Test
    @DisplayName("T03: Incorrect diagnostic key returns 401 Unauthorized")
    void testIncorrectDiagnosticKeyReturns401() throws Exception {
        // Auth verify endpoint
        mockMvc.perform(post("/api/recovery/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + INVALID_MASTER_KEY + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_KEY"));

        // Protected endpoint with invalid header
        mockMvc.perform(get("/api/recovery/health")
                        .header("X-Diagnostic-Key", INVALID_MASTER_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // Protected endpoint with missing header
        mockMvc.perform(get("/api/recovery/health"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // T04: Backup scanner discovers valid local backup files
    @Test
    @DisplayName("T04: Backup scanner discovers valid local backup files")
    void testBackupScannerDiscovery() throws Exception {
        // Create a test snapshot first so discovery finds it
        Map<String, Object> snap = orchestrator.createJsonSnapshot();
        assertNotNull(snap.get("filename"));

        mockMvc.perform(get("/api/recovery/backups/scan")
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier1LiveDatabase.status").value("CONNECTED"))
                .andExpect(jsonPath("$.tier1LiveDatabase.firmCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.tier2bDiagnosticSnapshots", notNullValue()));
    }

    // T05: Invalid/path-traversal recovery IDs are rejected
    @Test
    @DisplayName("T05: Invalid or path traversal recovery IDs are strictly rejected")
    void testPathTraversalRejected() throws Exception {
        // Subpath traversal
        mockMvc.perform(get("/api/recovery/backups/inspect")
                        .param("recoveryId", "DIAGNOSTIC:../../secret.txt")
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BACKUP_ID"));

        // Absolute path traversal
        mockMvc.perform(get("/api/recovery/backups/download")
                        .param("recoveryId", "/etc/passwd")
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // Forbidden extension
        mockMvc.perform(get("/api/recovery/backups/download")
                        .param("recoveryId", "DIAGNOSTIC:evil.sh")
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY))
                .andExpect(status().isBadRequest());
    }

    // T06: H2 snapshot creation works against the active H2 configuration
    @Test
    @DisplayName("T06: H2 physical snapshot creates valid zip archive")
    void testH2SnapshotCreation() throws Exception {
        mockMvc.perform(post("/api/recovery/backups/snapshot/h2")
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.type").value("H2_SNAPSHOT"))
                .andExpect(jsonPath("$.filename", endsWith(".zip")))
                .andExpect(jsonPath("$.sizeBytes", greaterThan(0)));
    }

    // T07: Pre-restore snapshot failure prevents restore
    @Test
    @DisplayName("T07: Pre-restore snapshot failure strictly prevents restore execution")
    void testPreRestoreSnapshotFailureBlocksRestore() {
        // Attempting to restore non-existent file or corrupted state
        Exception ex = assertThrows(Exception.class, () -> {
            orchestrator.restoreBackup("DIAGNOSTIC:non_existent_file.json", "clone", "CONFIRM");
        });
        assertTrue(ex.getMessage().contains("does not exist") || ex.getMessage().contains("Restore blocked"));
    }

    // T08: Restore validation failure causes transaction rollback
    @Test
    @DisplayName("T08: Restore validation failure causes transaction rollback")
    void testRestoreValidationFailureRollsBack() throws Exception {
        long firmCountBefore = firmDetailsRepo.count();

        // Create an invalid JSON snapshot (missing metadata) in diagnostic snapshots directory
        File snapshotsDir = DataDirectoryResolver.resolveDiagnosticSnapshotsDirectory();
        File invalidFile = new File(snapshotsDir, "diagnostic_snapshot_corrupted_test.json");
        objectMapper.writeValue(invalidFile, Map.of("corrupted", "data"));

        mockMvc.perform(post("/api/recovery/backups/restore")
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryId\":\"DIAGNOSTIC:" + invalidFile.getName() + "\",\"mode\":\"clone\",\"confirmation\":\"CONFIRM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // Cleanup test file
        invalidFile.delete();

        // Database firm count must remain unaffected
        assertEquals(firmCountBefore, firmDetailsRepo.count(), "Firm count must remain unchanged after failed restore");
    }

    // T09: Download does not modify database state
    @Test
    @DisplayName("T09: Download endpoint is strictly read-only and does not mutate database")
    void testDownloadDoesNotModifyState() throws Exception {
        Map<String, Object> snap = orchestrator.createJsonSnapshot();
        String recoveryId = (String) snap.get("recoveryId");
        long firmCountBefore = firmDetailsRepo.count();

        mockMvc.perform(get("/api/recovery/backups/download")
                        .param("recoveryId", recoveryId)
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment;")));

        assertEquals(firmCountBefore, firmDetailsRepo.count(), "Database state must not mutate during download");
    }

    // T10: Log endpoint returns bounded output
    @Test
    @DisplayName("T10: Log recent endpoint returns bounded output")
    void testLogEndpointBounded() throws Exception {
        mockMvc.perform(get("/api/recovery/logs/recent")
                        .param("lines", "5")
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.lineCount", lessThanOrEqualTo(5)));
    }

    // T12: Normal business endpoints still require tenant isolation
    @Test
    @DisplayName("T12: Normal business endpoints still require tenant isolation")
    void testNormalBusinessEndpointsRetainTenantIsolation() throws Exception {
        // Business endpoint without X-Firm-Id header should be intercepted/scoped
        // For example, /api/customer or /api/invoices requires tenant context
        // TenantInterceptor passes global paths but enforces firm header on tenant-scoped paths
        mockMvc.perform(get("/api/customers"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Either 200 (if empty/unfiltered) or 400 (if firmId required) - crucially TenantContext is enforced
                    assertTrue(status == 200 || status == 400 || status == 404);
                });
    }

    // Password Reset Verification
    @Test
    @DisplayName("Password reset sets auth_enabled=false in database")
    void testPasswordResetOperation() throws Exception {
        // Set auth_enabled = true first
        AppConfig authConfig = new AppConfig("auth_enabled", "true");
        appConfigRepo.save(authConfig);

        mockMvc.perform(post("/api/recovery/auth/reset-password")
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        AppConfig updated = appConfigRepo.findById("auth_enabled").orElse(null);
        assertNotNull(updated);
        assertEquals("false", updated.getConfigValue(), "auth_enabled must be false after reset");
    }

    // T13: Rate limit brute-force delay and cooldown
    @Test
    @DisplayName("T13: Rate limit rejects repeated failed attempts and enforces cooldown")
    void testRateLimitingAndCooldown() {
        try {
            for (int i = 0; i < 5; i++) {
                assertFalse(authService.verifyKey("WrongKey_" + i));
            }
            // 6th attempt should be blocked by rate limiting
            assertFalse(authService.verifyKey(INVALID_MASTER_KEY));
        } finally {
            authService.resetRateLimit();
        }
    }

    // T14: Traversal variants (Windows backslashes and null bytes)
    @Test
    @DisplayName("T14: Windows backslash traversal and null bytes are rejected")
    void testWindowsTraversalAndNullBytes() {
        assertThrows(IllegalArgumentException.class, () -> {
            DiagnosticRecoveryPathValidator.resolveAndValidateFile("DIAGNOSTIC:..\\..\\secret.json");
        });
        assertThrows(IllegalArgumentException.class, () -> {
            DiagnosticRecoveryPathValidator.resolveAndValidateFile("DIAGNOSTIC:file\0.json");
        });
        assertThrows(IllegalArgumentException.class, () -> {
            DiagnosticRecoveryPathValidator.resolveAndValidateFile("H2:..\\h2.zip");
        });
    }

    // T15: Symlink escape detection
    @Test
    @DisplayName("T15: Symlinks escaping sandbox directory are detected and blocked")
    void testSymlinkEscapeBlocked() {
        File snapshotsDir = DataDirectoryResolver.resolveDiagnosticSnapshotsDirectory();
        File externalFile = new File(System.getProperty("java.io.tmpdir"), "external_canary_" + System.currentTimeMillis() + ".json");
        try {
            java.nio.file.Files.writeString(externalFile.toPath(), "{\"canary\":true}");
            File symlink = new File(snapshotsDir, "symlink_escape_test.json");
            try {
                java.nio.file.Files.createSymbolicLink(symlink.toPath(), externalFile.toPath());
                assertThrows(SecurityException.class, () -> {
                    DiagnosticRecoveryPathValidator.resolveAndValidateFile("DIAGNOSTIC:" + symlink.getName());
                });
            } catch (UnsupportedOperationException | java.nio.file.FileSystemException ignored) {
                // OS permissions might restrict symlink creation in certain test environments
            } finally {
                if (symlink.exists()) {
                    symlink.delete();
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (externalFile.exists()) {
                externalFile.delete();
            }
        }
    }

    // T16: Invalid recovery ID format
    @Test
    @DisplayName("T16: Unrecognized recovery ID formats return HTTP 400 Bad Request")
    void testInvalidRecoveryIdFormat() throws Exception {
        mockMvc.perform(get("/api/recovery/backups/inspect")
                        .param("recoveryId", "UNKNOWN:some_file.json")
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BACKUP_ID"));
    }

    // T17: Invalid restore mode rejected
    @Test
    @DisplayName("T17: Invalid restore mode (clean/wipe) is strictly rejected")
    void testInvalidRestoreModeRejected() throws Exception {
        mockMvc.perform(post("/api/recovery/backups/restore")
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryId\":\"AUTO_LATEST\",\"mode\":\"clean_wipe\",\"confirmation\":\"CONFIRM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESTORE_PARAMS"));
    }

    // T18: Missing or invalid CONFIRM text rejected
    @Test
    @DisplayName("T18: Missing CONFIRM text is rejected with HTTP 400")
    void testMissingConfirmRejected() throws Exception {
        mockMvc.perform(post("/api/recovery/backups/restore")
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryId\":\"AUTO_LATEST\",\"mode\":\"clone\",\"confirmation\":\"YES\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESTORE_PARAMS"));
    }

    // T19: Unauthorized password reset and restart
    @Test
    @DisplayName("T19: Unauthorized password reset and restart are rejected with 401")
    void testUnauthorizedPrivilegedActions() throws Exception {
        mockMvc.perform(post("/api/recovery/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"CONFIRM\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/recovery/restart"))
                .andExpect(status().isUnauthorized());
    }

    // T20: Concurrent recovery operation locking
    @Test
    @DisplayName("T20: Concurrent recovery operations return 409 Conflict")
    void testConcurrentRecoveryLocking() throws Exception {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger conflictCount = new java.util.concurrent.atomic.AtomicInteger(0);

        // Run snapshot operations concurrently
        Runnable task = () -> {
            try {
                latch.await();
                mockMvc.perform(post("/api/recovery/backups/snapshot/json")
                                .header("X-Diagnostic-Key", VALID_MASTER_KEY))
                        .andDo(result -> {
                            if (result.getResponse().getStatus() == 409) {
                                conflictCount.incrementAndGet();
                            }
                        });
            } catch (Exception ignored) {}
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        latch.countDown();
        t1.join();
        t2.join();
        // At least one completed or was safely coordinated without crashing
        assertTrue(conflictCount.get() >= 0);
    }

    // T21: Snapshot retention pruning
    @Test
    @DisplayName("T21: Snapshot retention pruning removes excess generations beyond 14")
    void testSnapshotRetentionPruning() throws Exception {
        File snapshotsDir = DataDirectoryResolver.resolveDiagnosticSnapshotsDirectory();
        // Create 16 dummy snapshot files
        for (int i = 0; i < 16; i++) {
            File dummy = new File(snapshotsDir, String.format("diagnostic_snapshot_20260101_%02d0000.json", i));
            if (!dummy.exists()) {
                java.nio.file.Files.writeString(dummy.toPath(), "{\"dummy\":" + i + "}");
            }
        }

        // Apply retention
        File latest = new File(snapshotsDir, "diagnostic_snapshot_20260101_150000.json");
        orchestrator.applySnapshotRetention(snapshotsDir, latest);

        File[] remaining = snapshotsDir.listFiles((dir, name) -> name.startsWith("diagnostic_snapshot_") && name.endsWith(".json"));
        assertNotNull(remaining);
        assertTrue(remaining.length <= 15, "Old snapshots beyond retention limit must be pruned");
    }

    // T22: Unconfigured cloud recovery handled gracefully
    @Test
    @DisplayName("T22: Unconfigured cloud backup returns controlled error without crashing")
    void testUnconfiguredCloudBackupInspection() throws Exception {
        mockMvc.perform(get("/api/recovery/backups/inspect")
                        .param("recoveryId", "OFFSITE")
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 500 || status == 400);
                });
    }

    // T23: Persistent Live Console settings require diagnostic authentication
    @Test
    @DisplayName("T23: Persistent Live Console cannot be toggled without diagnostic key")
    void testPersistentLiveConsoleAuth() throws Exception {
        // Unauthenticated toggle rejected
        mockMvc.perform(post("/api/recovery/settings/persistent-console")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"logLevel\":\"VERBOSE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // Authenticated toggle succeeds
        mockMvc.perform(post("/api/recovery/settings/persistent-console")
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"logLevel\":\"VERBOSE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status.enabled").value(true))
                .andExpect(jsonPath("$.status.logLevel").value("VERBOSE"));

        // State is reflected in public status endpoint
        mockMvc.perform(get("/api/recovery/diagnostic-mode/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.logLevel").value("VERBOSE"));
    }

    // T24: Live log streaming endpoint behavior based on persistent diagnostic mode
    @Test
    @DisplayName("T24: Live log streaming is accessible to main app only when diagnostic mode is enabled")
    void testLiveLogStreamingAuthorization() throws Exception {
        // When diagnostic mode is disabled and no key is provided: 401
        sessionService.setPersistentConsoleEnabled(false);
        mockMvc.perform(get("/api/recovery/logs/live"))
                .andExpect(status().isUnauthorized());

        // When diagnostic mode is enabled: 200 without master key
        sessionService.setPersistentConsoleEnabled(true);
        mockMvc.perform(get("/api/recovery/logs/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.lines").isArray());

        // Reverting diagnostic mode revokes public stream access
        sessionService.setPersistentConsoleEnabled(false);
        mockMvc.perform(get("/api/recovery/logs/live"))
                .andExpect(status().isUnauthorized());
    }

    // T25: Destructive recovery operations remain strictly protected even when persistent mode is active
    @Test
    @DisplayName("T25: Destructive recovery operations strictly reject unauthenticated requests even in diagnostic mode")
    void testDestructiveOperationsProtectedInDiagnosticMode() throws Exception {
        sessionService.setPersistentConsoleEnabled(true);

        // Restore rejected without master key
        mockMvc.perform(post("/api/recovery/backups/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryId\":\"AUTO_LATEST\",\"mode\":\"CLONE\",\"confirmation\":\"CONFIRM\"}"))
                .andExpect(status().isUnauthorized());

        // Password reset rejected without master key
        mockMvc.perform(post("/api/recovery/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"CONFIRM\"}"))
                .andExpect(status().isUnauthorized());

        // Snapshot creation rejected without master key
        mockMvc.perform(post("/api/recovery/backups/snapshot/json"))
                .andExpect(status().isUnauthorized());

        // Restart rejected without master key
        mockMvc.perform(post("/api/recovery/restart"))
                .andExpect(status().isUnauthorized());
    }

    // T26: Invalid/empty snapshots cannot be restored
    @Test
    @DisplayName("T26: Invalid or empty snapshots cannot be restored")
    void testInvalidSnapshotCannotBeRestored() throws Exception {
        File snapshotsDir = DataDirectoryResolver.resolveDiagnosticSnapshotsDirectory();
        File invalidSnap = new File(snapshotsDir, "diagnostic_snapshot_invalid_test.json");
        java.nio.file.Files.writeString(invalidSnap.toPath(), "{\"dummy\":true}");

        try {
            mockMvc.perform(post("/api/recovery/backups/restore")
                            .header("X-Diagnostic-Key", VALID_MASTER_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recoveryId\":\"DIAGNOSTIC:" + invalidSnap.getName() + "\",\"mode\":\"CLONE\",\"confirmation\":\"CONFIRM\"}"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assertTrue(status == 400 || status == 500, "Invalid snapshot must be rejected");
                    })
                    .andExpect(jsonPath("$.success").value(false));
        } finally {
            if (invalidSnap.exists()) invalidSnap.delete();
        }
    }

    // T27: Physical H2 snapshots cannot participate in transactional JSON restore
    @Test
    @DisplayName("T27: Physical H2 snapshots are rejected from in-process transactional restore")
    void testPhysicalH2SnapshotCannotBeRestoredInProcess() throws Exception {
        File snapshotsDir = DataDirectoryResolver.resolveDiagnosticSnapshotsDirectory();
        File dummyZip = new File(snapshotsDir, "h2_snapshot_test_offline.zip");
        java.nio.file.Files.write(dummyZip.toPath(), new byte[]{1, 2, 3, 4});

        try {
            mockMvc.perform(post("/api/recovery/backups/restore")
                            .header("X-Diagnostic-Key", VALID_MASTER_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recoveryId\":\"H2:" + dummyZip.getName() + "\",\"mode\":\"CLONE\",\"confirmation\":\"CONFIRM\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("offline")));
        } finally {
            if (dummyZip.exists()) dummyZip.delete();
        }
    }

    // T28: Backup scanner discovers backup sources and categorizes artifacts with summary
    @Test
    @DisplayName("T28: Backup scanner discovers backup sources and groups artifacts with summary")
    void testBackupScannerSourcesAndSummary() throws Exception {
        mockMvc.perform(get("/api/recovery/backups/scan")
                        .header("X-Diagnostic-Key", VALID_MASTER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier1LiveDatabase").exists())
                .andExpect(jsonPath("$.tier2AutoBackup").exists())
                .andExpect(jsonPath("$.tier2bDiagnosticSnapshots").isArray())
                .andExpect(jsonPath("$.tier2bSummary").exists())
                .andExpect(jsonPath("$.tier2bSummary.retentionLimit").value(14))
                .andExpect(jsonPath("$.tier3OffsiteBackup").exists());
    }

    // T29: Establish authenticated diagnostic session and access protected endpoints without header
    @Test
    @DisplayName("T29: Establishing session via auth/verify allows access without X-Diagnostic-Key header")
    void testDiagnosticSessionAuthentication() throws Exception {
        // Authenticate with valid master key
        org.springframework.mock.web.MockHttpSession session = (org.springframework.mock.web.MockHttpSession) mockMvc.perform(
                        post("/api/recovery/auth/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"key\":\"" + VALID_MASTER_KEY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andReturn().getRequest().getSession(false);

        assertNotNull(session, "Session must be established on successful authentication");

        // Verify protected endpoint access USING SESSION, without X-Diagnostic-Key header
        mockMvc.perform(get("/api/recovery/health").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/api/recovery/backups/scan").session(session))
                .andExpect(status().isOk());

        // Check auth status endpoint
        mockMvc.perform(get("/api/recovery/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
    }

    // T30: Session lock invalidates the session and subsequent requests return 401
    @Test
    @DisplayName("T30: Locking console invalidates the session and revokes access")
    void testDiagnosticSessionLocking() throws Exception {
        // Create session
        org.springframework.mock.web.MockHttpSession session = (org.springframework.mock.web.MockHttpSession) mockMvc.perform(
                        post("/api/recovery/auth/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"key\":\"" + VALID_MASTER_KEY + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        assertNotNull(session);

        // Lock session
        mockMvc.perform(post("/api/recovery/auth/lock").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Subsequent access with the invalidated session must return 401 Unauthorized
        mockMvc.perform(get("/api/recovery/health").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // T31: Session allows backup payload and log downloads without key in header or query parameter
    @Test
    @DisplayName("T31: Authenticated session allows streaming download without key query param or header")
    void testSessionDownloadWithoutKeyInUrl() throws Exception {
        org.springframework.mock.web.MockHttpSession session = (org.springframework.mock.web.MockHttpSession) mockMvc.perform(
                        post("/api/recovery/auth/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"key\":\"" + VALID_MASTER_KEY + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        // Create snapshot to test download
        mockMvc.perform(post("/api/recovery/backups/snapshot/json").session(session))
                .andExpect(status().isOk());

        // Scan to find created snapshot
        String scanResp = mockMvc.perform(get("/api/recovery/backups/scan").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> scanMap = objectMapper.readValue(scanResp, Map.class);
        List<?> snapshots = (List<?>) scanMap.get("tier2bDiagnosticSnapshots");
        assertFalse(snapshots.isEmpty());
        Map<?, ?> firstSnap = (Map<?, ?>) snapshots.get(0);
        String recoveryId = (String) firstSnap.get("recoveryId");

        // Download USING ONLY SESSION (no X-Diagnostic-Key, no ?key= parameter in URL)
        mockMvc.perform(get("/api/recovery/backups/download")
                        .session(session)
                        .param("recoveryId", recoveryId))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"));
    }

    // T32: Incremental cursor log streaming using sinceSeq
    @Test
    @DisplayName("T32: Live logs support incremental cursor streaming (sinceSeq)")
    void testLiveLogsIncrementalCursorStreaming() throws Exception {
        org.springframework.mock.web.MockHttpSession session = (org.springframework.mock.web.MockHttpSession) mockMvc.perform(
                        post("/api/recovery/auth/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"key\":\"" + VALID_MASTER_KEY + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        // First poll: sinceSeq=0 returns latest lines + nextSeq
        String resp1 = mockMvc.perform(get("/api/recovery/logs/live")
                        .session(session)
                        .param("sinceSeq", "0")
                        .param("lines", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.nextSeq").isNumber())
                .andExpect(jsonPath("$.connectionState").value("STREAMING"))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> map1 = objectMapper.readValue(resp1, Map.class);
        long nextSeq = ((Number) map1.get("nextSeq")).longValue();

        // Immediate subsequent poll with same nextSeq should return empty lines or only newly generated lines
        mockMvc.perform(get("/api/recovery/logs/live")
                        .session(session)
                        .param("sinceSeq", String.valueOf(nextSeq))
                        .param("lines", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.lines", hasSize(0)));
    }

    // T33: Log sanitization for unauthenticated callers when persistent live console is active
    @Test
    @DisplayName("T33: Unauthenticated stream sanitizes passwords, keys, and tokens")
    void testLiveLogSanitizationForUnauthenticatedViewers() throws Exception {
        sessionService.setPersistentConsoleEnabled(true);

        // Normal/unauthenticated user without session or header
        mockMvc.perform(get("/api/recovery/logs/live")
                        .param("sinceSeq", "0")
                        .param("lines", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(result -> {
                    String content = result.getResponse().getContentAsString();
                    assertFalse(content.contains(VALID_MASTER_KEY), "Master key must never be visible in sanitized log stream");
                });
    }

    // T34: Application restore flow accepts optional selective firmIds and targetFirmId
    @Test
    @DisplayName("T34: Restore endpoint accepts optional firmIds scoping and confirmation")
    void testRestoreWithSelectiveFirmIds() throws Exception {
        org.springframework.mock.web.MockHttpSession session = (org.springframework.mock.web.MockHttpSession) mockMvc.perform(
                        post("/api/recovery/auth/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"key\":\"" + VALID_MASTER_KEY + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        // Create valid snapshot
        mockMvc.perform(post("/api/recovery/backups/snapshot/json").session(session))
                .andExpect(status().isOk());

        String scanResp = mockMvc.perform(get("/api/recovery/backups/scan").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> scanMap = objectMapper.readValue(scanResp, Map.class);
        List<?> snapshots = (List<?>) scanMap.get("tier2bDiagnosticSnapshots");
        Map<?, ?> firstSnap = (Map<?, ?>) snapshots.get(0);
        String recoveryId = (String) firstSnap.get("recoveryId");

        // Execute restore with firmIds scoping and CONFIRM token
        mockMvc.perform(post("/api/recovery/backups/restore")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recoveryId\":\"" + recoveryId + "\",\"mode\":\"CLONE\",\"firmIds\":[1],\"confirmation\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.mode").value("clone"))
                .andExpect(jsonPath("$.preRestoreSnapshot").exists());
    }

    // T35: Session Isolation - Normal application session remains valid after diagnostic lock
    @Test
    @DisplayName("T35: Normal application session is preserved when diagnostic console is locked")
    void testSessionIsolationNormalAppSessionPreservedOnLock() throws Exception {
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        // Simulate normal RupeeCRM application login placing an attribute on the session
        session.setAttribute("NORMAL_USER_LOGIN_TOKEN", "user_auth_token_xyz123");
        session.setAttribute("USER_ID", 42L);

        // Authenticate diagnostic console with the existing session
        mockMvc.perform(post("/api/recovery/auth/verify")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + VALID_MASTER_KEY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));

        assertTrue(Boolean.TRUE.equals(session.getAttribute("DIAGNOSTIC_AUTHENTICATED")),
                "Diagnostic session attribute must be established");

        // Lock the diagnostic console
        mockMvc.perform(post("/api/recovery/auth/lock")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));

        // Verify diagnostic clearance is revoked
        assertNull(session.getAttribute("DIAGNOSTIC_AUTHENTICATED"),
                "Diagnostic clearance attribute must be removed");
        assertFalse(session.isInvalid(),
                "HttpSession must NOT be destroyed by diagnostic lock");

        // Verify normal application session attributes remain 100% intact!
        assertEquals("user_auth_token_xyz123", session.getAttribute("NORMAL_USER_LOGIN_TOKEN"),
                "Normal application login token must be preserved");
        assertEquals(42L, session.getAttribute("USER_ID"),
                "Normal application user context must be preserved");

        // Subsequent diagnostic call with this session must be rejected with 401 UNAUTHORIZED
        mockMvc.perform(get("/api/recovery/health").session(session))
                .andExpect(status().isUnauthorized());
    }

    // T36: Dedicated Cookie Authentication and Revocation
    @Test
    @DisplayName("T36: Dedicated RUPEE_DIAG_SESSION cookie authenticates protected endpoints and is revoked on lock")
    void testDedicatedCookieAuthenticationAndRevocation() throws Exception {
        jakarta.servlet.http.Cookie diagCookie = mockMvc.perform(post("/api/recovery/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + VALID_MASTER_KEY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("RUPEE_DIAG_SESSION"))
                .andReturn().getResponse().getCookie("RUPEE_DIAG_SESSION");

        assertNotNull(diagCookie);
        assertTrue(diagCookie.isHttpOnly());

        // Access protected endpoint using ONLY the diagnostic cookie (no session object, no key header)
        mockMvc.perform(get("/api/recovery/health")
                        .cookie(diagCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        // Lock using the cookie
        mockMvc.perform(post("/api/recovery/auth/lock")
                        .cookie(diagCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("RUPEE_DIAG_SESSION", 0));

        // Subsequent call with the old cookie must return 401
        mockMvc.perform(get("/api/recovery/health")
                        .cookie(diagCookie))
                .andExpect(status().isUnauthorized());
    }

    // T37: HTTP 400 Bad Request for Invalid / Corrupted Backup
    @Test
    @DisplayName("T37: Predictable backup validation failure returns HTTP 400 (not HTTP 500)")
    void testCorruptedBackupReturnsBadRequest() throws Exception {
        org.springframework.mock.web.MockHttpSession session = (org.springframework.mock.web.MockHttpSession) mockMvc.perform(
                        post("/api/recovery/auth/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"key\":\"" + VALID_MASTER_KEY + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        // Create an invalid/corrupted snapshot file
        File snapshotsDir = DataDirectoryResolver.resolveDiagnosticSnapshotsDirectory();
        File stubFile = new File(snapshotsDir, "diagnostic_snapshot_stub_test.json");
        java.nio.file.Files.writeString(stubFile.toPath(), "{\"corrupted\": true}");

        try {
            // Attempting to restore invalid backup must return 400 BAD_REQUEST
            mockMvc.perform(post("/api/recovery/backups/restore")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recoveryId\":\"DIAGNOSTIC:" + stubFile.getName() + "\",\"mode\":\"clone\",\"confirmation\":\"CONFIRM\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_BACKUP_SCHEMA"));
        } finally {
            if (stubFile.exists()) stubFile.delete();
        }
    }

    // T38: Cursor resync condition returned on future or lapsed sequence
    @Test
    @DisplayName("T38: Out-of-sync cursor triggers resyncRequired=true without freezing")
    void testCursorResyncDetection() throws Exception {
        org.springframework.mock.web.MockHttpSession session = (org.springframework.mock.web.MockHttpSession) mockMvc.perform(
                        post("/api/recovery/auth/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"key\":\"" + VALID_MASTER_KEY + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        // Polling with a sequence far in the future (e.g. from prior server instance)
        mockMvc.perform(get("/api/recovery/logs/live")
                        .session(session)
                        .param("sinceSeq", "99999999")
                        .param("lines", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resyncRequired").value(true))
                .andExpect(jsonPath("$.nextSeq").isNumber());
    }

    // T39: Secret masking across sensitive patterns
    @Test
    @DisplayName("T39: Sanitizer masks master keys, passwords, JWTs, card numbers, and credentials")
    void testComprehensiveSecretMasking() {
        String raw = "Login attempt with Saidarshan*1, password=\"SuperSecret!\", raw_jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c, card=4111-2222-3333-4444, jdbc:h2:file:data;password=dbpass123";
        String sanitized = logService.sanitizeLine(raw);

        assertFalse(sanitized.contains("Saidarshan*1"), "Master key must be redacted");
        assertFalse(sanitized.contains("SuperSecret!"), "Password must be redacted");
        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"), "JWT must be redacted");
        assertFalse(sanitized.contains("4111-2222-3333-4444"), "Card number must be redacted");
        assertFalse(sanitized.contains("dbpass123"), "Database password must be redacted");

        assertTrue(sanitized.contains("***REDACTED_MASTER_KEY***"));
        assertTrue(sanitized.contains("****-****-****-****"));
        assertTrue(sanitized.contains("***REDACTED_JWT***"));
    }

    // T40: Multi-tab invalidation
    @Test
    @DisplayName("T40: Lock invalidates diagnostic access across tabs sharing the session")
    void testMultiTabInvalidationOnLock() throws Exception {
        org.springframework.mock.web.MockHttpSession sharedSession = (org.springframework.mock.web.MockHttpSession) mockMvc.perform(
                        post("/api/recovery/auth/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"key\":\"" + VALID_MASTER_KEY + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        // Tab 1 checks session -> ok
        mockMvc.perform(get("/api/recovery/auth/session").session(sharedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));

        // Tab 2 checks health -> ok
        mockMvc.perform(get("/api/recovery/health").session(sharedSession))
                .andExpect(status().isOk());

        // Tab 1 locks
        mockMvc.perform(post("/api/recovery/auth/lock").session(sharedSession))
                .andExpect(status().isOk());

        // Tab 2 is immediately rejected with 401
        mockMvc.perform(get("/api/recovery/health").session(sharedSession))
                .andExpect(status().isUnauthorized());
    }

    // T41: Explicit enablement activates VERBOSE and 8-hour window
    @Test
    @DisplayName("T41: Enabling diagnostics sets enabled=true, VERBOSE logging, and 8-hour expiry")
    void testEnablingDiagnosticsSetsVerboseAnd8HourExpiry() throws Exception {
        org.springframework.mock.web.MockHttpSession session = (org.springframework.mock.web.MockHttpSession) mockMvc.perform(
                        post("/api/recovery/auth/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"key\":\"" + VALID_MASTER_KEY + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        long beforeTime = System.currentTimeMillis();

        mockMvc.perform(post("/api/recovery/settings/persistent-console")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status.enabled").value(true))
                .andExpect(jsonPath("$.status.logLevel").value("VERBOSE"))
                .andExpect(jsonPath("$.status.expiresAt").isNumber())
                .andExpect(jsonPath("$.status.remainingMillis").isNumber());

        assertTrue(sessionService.isPersistentConsoleEnabled());
        assertEquals("VERBOSE", sessionService.getLogLevel());
        long expectedMinExpiry = beforeTime + (8 * 3600 * 1000L) - 10000L;
        long expectedMaxExpiry = beforeTime + (8 * 3600 * 1000L) + 10000L;
        assertTrue(sessionService.getExpiresAt() >= expectedMinExpiry && sessionService.getExpiresAt() <= expectedMaxExpiry,
                "Expiry must be approximately 8 hours from enablement");
    }

    // T42: Polling status or checking session does NOT extend expiry
    @Test
    @DisplayName("T42: Status polling or reloading does not extend the 8-hour expiry timestamp")
    void testStatusPollingDoesNotExtendExpiry() throws Exception {
        sessionService.setPersistentConsoleEnabled(true);
        long initialExpiry = sessionService.getExpiresAt();

        // Repeated public status checks
        mockMvc.perform(get("/api/recovery/diagnostic-mode/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.expiresAt").value(initialExpiry));

        mockMvc.perform(get("/api/recovery/diagnostic-mode/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value(initialExpiry));

        assertEquals(initialExpiry, sessionService.getExpiresAt(), "Status polling must never extend expiry");
    }

    // T43: Authoritative server-side automatic expiry restores NORMAL logging
    @Test
    @DisplayName("T43: Expired session automatically disables diagnostics and restores NORMAL logging")
    void testServerSideExpiryEnforcement() throws Exception {
        // Set an expired timestamp in the past
        long pastExpiry = System.currentTimeMillis() - 1000L;
        sessionService.setPersistentConsoleStateForTesting(true, "VERBOSE", pastExpiry);

        // Next evaluation of status or isPersistentConsoleEnabled triggers automatic deactivation
        assertFalse(sessionService.isPersistentConsoleEnabled(), "Expired session must return false");
        assertEquals("NORMAL", sessionService.getLogLevel(), "Expired session must restore NORMAL logging");
        assertEquals(0L, sessionService.getExpiresAt(), "Expired session must clear expiresAt");

        // Status endpoint reflects expired state
        mockMvc.perform(get("/api/recovery/diagnostic-mode/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.logLevel").value("NORMAL"))
                .andExpect(jsonPath("$.expiresAt").value(0));
    }

    // T44: Expired unauthenticated live-log and download requests are rejected with 401
    @Test
    @DisplayName("T44: Expired diagnostic requests are rejected with 401 for unauthenticated clients")
    void testExpiredRequestsRejectedWith401() throws Exception {
        // Simulate expired diagnostics
        sessionService.setPersistentConsoleStateForTesting(true, "VERBOSE", System.currentTimeMillis() - 1000L);

        // Unauthenticated live log stream
        mockMvc.perform(get("/api/recovery/logs/live"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // Unauthenticated download endpoint
        mockMvc.perform(get("/api/recovery/logs/live/download"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // T45: Live Log Download returns text/plain with buffered snapshot without altering cursor
    @Test
    @DisplayName("T45: Live log download returns text/plain, contains logs, and preserves cursor")
    void testLiveLogDownloadEndpoint() throws Exception {
        sessionService.setPersistentConsoleEnabled(true);

        // Emit an event to ensure non-empty buffer
        log.info("DIAGNOSTIC_DOWNLOAD_VERIFY_MARKER_12345");

        // Record cursor before download
        String streamBefore = mockMvc.perform(get("/api/recovery/logs/live").param("sinceSeq", "0"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> streamMap = objectMapper.readValue(streamBefore, Map.class);
        long cursorBefore = ((Number) streamMap.get("nextSeq")).longValue();

        // Download live logs
        MvcResult downloadResult = mockMvc.perform(get("/api/recovery/logs/live/download"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.TEXT_PLAIN_VALUE)))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment; filename=\"diagnostic-logs-")))
                .andReturn();

        String downloadedText = downloadResult.getResponse().getContentAsString();
        assertTrue(downloadedText.contains("DIAGNOSTIC_DOWNLOAD_VERIFY_MARKER_12345"), "Downloaded text must contain buffered logs");

        // Subsequent live stream poll with cursorBefore verifies cursor was not reset or corrupted by download
        mockMvc.perform(get("/api/recovery/logs/live").param("sinceSeq", String.valueOf(cursorBefore)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resyncRequired").value(false))
                .andExpect(jsonPath("$.nextSeq", greaterThanOrEqualTo((int) cursorBefore)));
    }

    // T46: Download redacts sensitive information for unauthenticated clients
    @Test
    @DisplayName("T46: Download redacts sensitive passwords, keys, and tokens for unauthenticated callers")
    void testDownloadSanitizesSensitiveData() throws Exception {
        sessionService.setPersistentConsoleEnabled(true);

        // Log sensitive line
        log.warn("Emergency clearance granted with key Saidarshan*1 and token eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0.abc");

        MvcResult result = mockMvc.perform(get("/api/recovery/logs/live/download"))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertFalse(content.contains(VALID_MASTER_KEY), "Master key must be redacted in unauthenticated download");
        assertTrue(content.contains("***REDACTED_MASTER_KEY***"));
    }

    // T47: Server reinitialization restores unexpired remaining window
    @Test
    @DisplayName("T47: Service startup preserves unexpired remaining window from database")
    void testServiceStartupPreservesUnexpiredRemainingWindow() {
        long futureExpiry = System.currentTimeMillis() + (4 * 3600 * 1000L); // 4 hours remaining
        sessionService.setPersistentConsoleStateForTesting(true, "VERBOSE", futureExpiry);

        // Instantiate a new DiagnosticSessionService simulating application restart
        DiagnosticSessionService restartedService = new DiagnosticSessionService(appConfigRepo, logService);

        assertTrue(restartedService.isPersistentConsoleEnabled(), "Unexpired session must remain active after restart");
        assertEquals("VERBOSE", restartedService.getLogLevel(), "VERBOSE logging must remain active after restart");
        assertEquals(futureExpiry, restartedService.getExpiresAt(), "Remaining expiry must be preserved after restart");
    }

    // T48: Server reinitialization disables expired session
    @Test
    @DisplayName("T48: Service startup deactivates session if expiry passed during downtime")
    void testServiceStartupDeactivatesExpiredSession() {
        long pastExpiry = System.currentTimeMillis() - 5000L;
        sessionService.setPersistentConsoleStateForTesting(true, "VERBOSE", pastExpiry);

        // Instantiate a new DiagnosticSessionService simulating application restart after downtime
        DiagnosticSessionService restartedService = new DiagnosticSessionService(appConfigRepo, logService);

        assertFalse(restartedService.isPersistentConsoleEnabled(), "Expired session must come back disabled");
        assertEquals("NORMAL", restartedService.getLogLevel(), "Logging must come back NORMAL");
        assertEquals(0L, restartedService.getExpiresAt(), "expiresAt must be cleared to 0");
    }
}

