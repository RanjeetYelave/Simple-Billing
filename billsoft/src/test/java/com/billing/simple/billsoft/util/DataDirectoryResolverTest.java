package com.billing.simple.billsoft.util;

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class DataDirectoryResolverTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("RUPEECRM_DATA_DIR");
        System.clearProperty("BILLSOFT_DATA_DIR");
    }

    @Test
    void testCustomSystemPropertyOverride(@TempDir File tempDir) {
        File customDir = new File(tempDir, "custom_rupeecrm_data");
        System.setProperty("RUPEECRM_DATA_DIR", customDir.getAbsolutePath());

        File resolved = DataDirectoryResolver.resolveDataDirectory();
        assertEquals(customDir.getAbsolutePath(), resolved.getAbsolutePath());
        assertTrue(resolved.exists());
    }

    @Test
    void testLegacyMigrationOnlyWhenTargetDatabaseMissing(@TempDir File tempDir) throws Exception {
        // Setup mock legacy directory
        File mockLegacyDir = new File(tempDir, "SimpleBilling");
        mockLegacyDir.mkdirs();
        File legacyDb = new File(mockLegacyDir, "database.mv.db");
        Files.writeString(legacyDb.toPath(), "LEGACY_DB_V1");

        File legacyBackupDir = new File(mockLegacyDir, "backups");
        legacyBackupDir.mkdirs();
        File legacyBackup = new File(legacyBackupDir, "autobackup_latest.json");
        Files.writeString(legacyBackup.toPath(), "{\"version\":1}");

        // Setup mock target directory
        File mockTargetDir = new File(tempDir, "RupeeCRM");
        // Point system property to simulate target
        System.setProperty("RUPEECRM_DATA_DIR", mockTargetDir.getAbsolutePath());

        File resolved = DataDirectoryResolver.resolveDataDirectory();
        assertEquals(mockTargetDir.getAbsolutePath(), resolved.getAbsolutePath());
        assertTrue(resolved.exists());
    }

    @Test
    void testMigrationNeverOverwritesExistingTargetData(@TempDir File tempDir) throws Exception {
        File targetDir = new File(tempDir, "target_rupee");
        targetDir.mkdirs();
        File targetDb = new File(targetDir, "database.mv.db");
        Files.writeString(targetDb.toPath(), "NEW_TARGET_DB_DATA");

        System.setProperty("RUPEECRM_DATA_DIR", targetDir.getAbsolutePath());
        File resolved = DataDirectoryResolver.resolveDataDirectory();

        assertEquals("NEW_TARGET_DB_DATA", Files.readString(new File(resolved, "database.mv.db").toPath()));
    }
}
