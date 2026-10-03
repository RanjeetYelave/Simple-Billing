package com.billing.simple.billsoft.util;

import java.io.File;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class DataDirectoryResolverTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("RUPEECRM_BASE_DIR");
        System.clearProperty("RUPEECRM_DATA_DIR");
        System.clearProperty("BILLSOFT_DATA_DIR");
    }

    @Test
    void testCustomDataDirOverride(@TempDir File tempDir) {
        File customDir = new File(tempDir, "custom_rupeecrm_data");
        System.setProperty("RUPEECRM_DATA_DIR", customDir.getAbsolutePath());

        File resolved = DataDirectoryResolver.resolveDataDirectory();
        assertEquals(customDir.getAbsolutePath(), resolved.getAbsolutePath());
        assertTrue(resolved.exists());
    }

    @Test
    void testCustomBaseDirResolution(@TempDir File tempDir) {
        File customBase = new File(tempDir, "custom_base");
        System.setProperty("RUPEECRM_BASE_DIR", customBase.getAbsolutePath());

        File dataDir = DataDirectoryResolver.resolveDataDirectory();
        File backupsDir = DataDirectoryResolver.resolveBackupsDirectory();
        File logsDir = DataDirectoryResolver.resolveLogsDirectory();
        File stagingDir = DataDirectoryResolver.resolveStagingDirectory();

        assertEquals(new File(customBase, "data").getAbsolutePath(), dataDir.getAbsolutePath());
        assertEquals(new File(customBase, "backups").getAbsolutePath(), backupsDir.getAbsolutePath());
        assertEquals(new File(customBase, "logs").getAbsolutePath(), logsDir.getAbsolutePath());
        assertEquals(new File(customBase, "staging").getAbsolutePath(), stagingDir.getAbsolutePath());

        assertTrue(dataDir.exists());
        assertTrue(backupsDir.exists());
        assertTrue(logsDir.exists());
        assertTrue(stagingDir.exists());
    }
}
