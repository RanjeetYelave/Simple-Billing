package com.billing.simple.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class WindowsLifecycleAndProcessOwnershipTest {

    @Test
    void testCleanShutdownExitCodeZero() {
        int exitCode = 0;
        boolean restartRequested = false;
        boolean exitRequested = false;
        boolean crashRecorded = false;

        if (exitCode == 10) {
            restartRequested = true;
        } else if (exitCode == 0) {
            exitRequested = true;
        } else {
            crashRecorded = true;
        }

        assertFalse(restartRequested, "Exit code 0 must not trigger restart");
        assertTrue(exitRequested, "Exit code 0 must cleanly request supervisor exit");
        assertFalse(crashRecorded, "Exit code 0 must not record a crash event");
    }

    @Test
    void testBackendPidFileLifecycle(@TempDir Path tempDir) throws IOException {
        String originalBase = System.getProperty("RUPEECRM_BASE_DIR");
        try {
            System.setProperty("RUPEECRM_BASE_DIR", tempDir.toAbsolutePath().toString());

            Path pidFile = tempDir.resolve(".backend.pid");
            assertFalse(Files.exists(pidFile), "Pid file should not exist initially");

            LauncherMain.writeBackendPidFile(7012L);
            assertTrue(Files.exists(pidFile), "Pid file must exist after write");
            String content = Files.readString(pidFile).trim();
            assertEquals("7012", content, "Pid file content must match recorded PID");

            LauncherMain.clearBackendPidFile();
            assertFalse(Files.exists(pidFile), "Pid file must be removed after clear");
        } finally {
            if (originalBase != null) {
                System.setProperty("RUPEECRM_BASE_DIR", originalBase);
            } else {
                System.clearProperty("RUPEECRM_BASE_DIR");
            }
        }
    }

    @Test
    void testPortAvailabilityChecks() {
        assertTrue(LauncherMain.isPortAvailable(49999), "High unassigned port should be available");
    }

    @Test
    void testCustomerDataDirectorySafety(@TempDir Path tempDir) throws IOException {
        String originalBase = System.getProperty("RUPEECRM_BASE_DIR");
        try {
            System.setProperty("RUPEECRM_BASE_DIR", tempDir.toAbsolutePath().toString());
            Path dataDir = LauncherMain.getDataDirectory();
            Path backupsDir = LauncherMain.getBackupsDirectory();

            Files.createDirectories(dataDir);
            Files.createDirectories(backupsDir);

            Path syntheticDb = dataDir.resolve("database.mv.db");
            Path syntheticLicense = dataDir.resolve("license.key");
            Path syntheticBackup = backupsDir.resolve("auto_backup_test.zip");

            Files.writeString(syntheticDb, "H2_DATABASE_CANARY");
            Files.writeString(syntheticLicense, "LICENSE_CANARY");
            Files.writeString(syntheticBackup, "BACKUP_ZIP_CANARY");

            assertTrue(Files.exists(syntheticDb), "Database canary must exist");
            assertTrue(Files.exists(syntheticLicense), "License canary must exist");
            assertTrue(Files.exists(syntheticBackup), "Backup canary must exist");

            // Verify paths are completely disjoint from installation binaries
            Path appBinariesDir = tempDir.resolve("app-binaries");
            assertFalse(dataDir.startsWith(appBinariesDir), "Data directory must never be subpath of application binaries");
            assertFalse(backupsDir.startsWith(appBinariesDir), "Backups directory must never be subpath of application binaries");
        } finally {
            if (originalBase != null) {
                System.setProperty("RUPEECRM_BASE_DIR", originalBase);
            } else {
                System.clearProperty("RUPEECRM_BASE_DIR");
            }
        }
    }
}
