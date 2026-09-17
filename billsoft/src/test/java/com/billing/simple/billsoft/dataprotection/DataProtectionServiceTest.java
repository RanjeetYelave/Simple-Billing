package com.billing.simple.billsoft.dataprotection;

import com.billing.simple.billsoft.licensing.DataProtectionEntitlement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DataProtectionServiceTest {

    @TempDir
    File tempBackupDir;

    @TempDir
    File tempStatusDir;

    private DataProtectionEntitlement mockEntitlement;
    private BackupStorageProvider mockStorageProvider;
    private DataProtectionCrypto crypto;
    private File statusFile;

    private DataProtectionService service;

    @BeforeEach
    public void setUp() {
        mockEntitlement = mock(DataProtectionEntitlement.class);
        mockStorageProvider = mock(BackupStorageProvider.class);
        crypto = new DataProtectionCrypto();
        statusFile = new File(tempStatusDir, "dp_status.json");

        when(mockEntitlement.isDataProtectionActive()).thenReturn(true);
        when(mockEntitlement.isDataProtectionEnabled()).thenReturn(true);
        when(mockEntitlement.getLicenseId()).thenReturn("LIC-TEST-77");
        when(mockEntitlement.getMachineId()).thenReturn("MID-MACHINE-88");
        when(mockEntitlement.getDataProtectionExpiresAt()).thenReturn(Instant.now().plus(Duration.ofDays(30)));
    }

    @org.junit.jupiter.api.AfterEach
    public void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    public void testSuccessfulBackupProcess() throws Exception {
        // Create local backup file
        File latestBackup = new File(tempBackupDir, "autobackup_latest.json");
        Files.writeString(latestBackup.toPath(), "{\"testKey\":\"testValue\"}");

        when(mockStorageProvider.uploadBackup(eq("MID-MACHINE-88"), any(), any()))
                .thenReturn(BackupStorageProvider.UploadResult.ok("new-sha-123"));

        this.service = new DataProtectionService(mockEntitlement, mockStorageProvider, crypto, tempBackupDir, statusFile);

        Map<String, Object> result = service.triggerBackupNow(true);

        assertTrue((Boolean) result.get("success"));
        assertNotNull(result.get("lastSuccessfulBackup"));
        assertEquals("new-sha-123", service.getCurrentStatus().getLastBlobSha());
        assertNull(service.getCurrentStatus().getLastError());
    }

    @Test
    public void testNetworkFailureImmunityAndIsolation() throws Exception {
        File latestBackup = new File(tempBackupDir, "autobackup_latest.json");
        Files.writeString(latestBackup.toPath(), "{\"data\":\"preserved\"}");

        // Simulate complete network failure / HTTP 500
        when(mockStorageProvider.uploadBackup(any(), any(), any()))
                .thenReturn(BackupStorageProvider.UploadResult.error(DataProtectionErrorCode.DP_007.formatMessage(), 500, DataProtectionErrorCode.DP_007));

        this.service = new DataProtectionService(mockEntitlement, mockStorageProvider, crypto, tempBackupDir, statusFile);

        // Trigger backup - Must NEVER throw unhandled exception or crash the application
        Map<String, Object> result = assertDoesNotThrow(() -> service.triggerBackupNow(true));

        assertFalse((Boolean) result.get("success"));
        assertTrue(service.getCurrentStatus().getLastError().contains("DP-007"));
        // Local backup file remains intact and unmodified
        assertTrue(latestBackup.exists());
    }

    @Test
    public void testAuthenticationFailureErrorMapping() throws Exception {
        File latestBackup = new File(tempBackupDir, "autobackup_latest.json");
        Files.writeString(latestBackup.toPath(), "{\"data\":\"test\"}");

        // Simulate HTTP 401 Bad credentials
        when(mockStorageProvider.uploadBackup(any(), any(), any()))
                .thenReturn(BackupStorageProvider.UploadResult.error(DataProtectionErrorCode.DP_002.formatMessage(), 401, DataProtectionErrorCode.DP_002));

        this.service = new DataProtectionService(mockEntitlement, mockStorageProvider, crypto, tempBackupDir, statusFile);

        Map<String, Object> result = service.triggerBackupNow(true);
        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("message")).contains("DP-002"));
        assertTrue(service.getCurrentStatus().getLastError().contains("DP-002"));
    }

    @Test
    public void testRepositoryInaccessibleErrorMapping() throws Exception {
        File latestBackup = new File(tempBackupDir, "autobackup_latest.json");
        Files.writeString(latestBackup.toPath(), "{\"data\":\"test\"}");

        // Simulate HTTP 404 Repo not found
        when(mockStorageProvider.uploadBackup(any(), any(), any()))
                .thenReturn(BackupStorageProvider.UploadResult.error(DataProtectionErrorCode.DP_003.formatMessage(), 404, DataProtectionErrorCode.DP_003));

        this.service = new DataProtectionService(mockEntitlement, mockStorageProvider, crypto, tempBackupDir, statusFile);

        Map<String, Object> result = service.triggerBackupNow(true);
        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("message")).contains("DP-003"));
    }

    @Test
    public void testNetworkTimeoutErrorMapping() throws Exception {
        File latestBackup = new File(tempBackupDir, "autobackup_latest.json");
        Files.writeString(latestBackup.toPath(), "{\"data\":\"test\"}");

        // Simulate Network timeout
        when(mockStorageProvider.uploadBackup(any(), any(), any()))
                .thenReturn(BackupStorageProvider.UploadResult.error(DataProtectionErrorCode.DP_004.formatMessage(), 0, DataProtectionErrorCode.DP_004));

        this.service = new DataProtectionService(mockEntitlement, mockStorageProvider, crypto, tempBackupDir, statusFile);

        Map<String, Object> result = service.triggerBackupNow(true);
        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("message")).contains("DP-004"));
    }

    @Test
    public void testMissingLocalSnapshotErrorMapping() {
        // Ensure no autobackup_latest.json in directory
        this.service = new DataProtectionService(mockEntitlement, mockStorageProvider, crypto, tempBackupDir, statusFile);

        Map<String, Object> result = service.triggerBackupNow(true);
        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("message")).contains("DP-001"));
        assertTrue(service.getCurrentStatus().getLastError().contains("DP-001"));
    }

    @Test
    public void testVaultTransportDescriptorResolution() {
        String descriptor = VaultTransportRegistry.resolveDefaultDescriptor();
        assertNotNull(descriptor);
        assertFalse(descriptor.isBlank());
        assertEquals(93, descriptor.length());
        assertTrue(descriptor.startsWith("github_"));
    }

    @Test
    public void testWeeklyIntervalRuleSkipsRemoteCalls() throws Exception {
        File latestBackup = new File(tempBackupDir, "autobackup_latest.json");
        Files.writeString(latestBackup.toPath(), "{\"data\":\"test\"}");

        this.service = new DataProtectionService(mockEntitlement, mockStorageProvider, crypto, tempBackupDir, statusFile);

        // Mark last successful backup as 3 days ago (< 7 days)
        DataProtectionStatus status = service.getCurrentStatus();
        status.setLastSuccessfulCloudBackupAt(Instant.now().minus(Duration.ofDays(3)));
        service.saveStatus(status);

        // Scheduled check runs
        service.performScheduledBackupCheck();

        // Must NOT have called the storage provider (0 remote calls)
        verify(mockStorageProvider, never()).uploadBackup(any(), any(), any());
    }

    @Test
    public void testInactiveEntitlementSkipsRemoteCalls() {
        when(mockEntitlement.isDataProtectionActive()).thenReturn(false);

        this.service = new DataProtectionService(mockEntitlement, mockStorageProvider, crypto, tempBackupDir, statusFile);
        service.performScheduledBackupCheck();

        // Zero remote calls
        verify(mockStorageProvider, never()).uploadBackup(any(), any(), any());
    }
}
