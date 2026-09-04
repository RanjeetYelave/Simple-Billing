package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.FirmDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AutoBackupServiceTest {

    @Mock
    private BackupService backupService;

    @TempDir
    Path tempDir;

    private AutoBackupService autoBackupService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        System.setProperty("BILLSOFT_DATA_DIR", tempDir.toAbsolutePath().toString());
        autoBackupService = new AutoBackupService(backupService);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("BILLSOFT_DATA_DIR");
    }

    @Test
    void testGetBackupDirectory() {
        File backupDir = autoBackupService.getBackupDirectory();
        assertNotNull(backupDir);
        assertTrue(backupDir.exists());
        assertTrue(backupDir.isDirectory());
        assertEquals("backups", backupDir.getName());
    }

    @Test
    void testRunAutoBackupCreatesFileAndPrunesOld() throws Exception {
        BackupDTO mockBackup = new BackupDTO();
        mockBackup.setMetadata(new HashMap<>());
        FirmDetails firm = new FirmDetails();
        firm.setId(1L);
        firm.setFirmName("Auto Firm");
        mockBackup.setAllFirms(Collections.singletonList(firm));
        when(backupService.exportAllData()).thenReturn(mockBackup);

        // Pre-create an old stray backup file in the backups folder
        File backupDir = autoBackupService.getBackupDirectory();
        File oldStray = new File(backupDir, "autobackup_temp_12345.json");
        assertTrue(oldStray.createNewFile());

        Map<String, Object> result = autoBackupService.runAutoBackup();
        assertNotNull(result);
        assertEquals("SUCCESS", result.get("status"));

        File latestFile = new File(backupDir, "autobackup_latest.json");
        assertTrue(latestFile.exists());
        assertTrue(latestFile.length() > 0);

        // Verify old stray was deleted
        assertFalse(oldStray.exists());

        // Verify getStatus returns valid details
        Map<String, Object> status = autoBackupService.getStatus();
        assertNotNull(status);
        assertTrue((Boolean) status.get("fileExists"));
        assertEquals(latestFile.getAbsolutePath(), status.get("filePath"));
    }

    @Test
    void testCheckAndRunStartupAutoBackup() {
        BackupDTO mockBackup = new BackupDTO();
        mockBackup.setMetadata(new HashMap<>());
        when(backupService.exportAllData()).thenReturn(mockBackup);

        autoBackupService.checkAndRunStartupAutoBackup();
        verify(backupService, atLeastOnce()).exportAllData();
    }
}
