package com.billing.simple.billsoft.dataprotection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class GitHubStorageProviderTest {

    @TempDir
    File tempDir;

    private File emptyConfigFile;

    @BeforeEach
    public void setUp() {
        emptyConfigFile = new File(tempDir, "non_existent_vault_config.json");
        System.clearProperty("rupeecrm.dataprotection.token");
        System.clearProperty("rupeecrm.dataprotection.repo");
        System.clearProperty("rupeecrm.licensing.token");
    }

    @AfterEach
    public void tearDown() {
        System.clearProperty("rupeecrm.dataprotection.token");
        System.clearProperty("rupeecrm.dataprotection.repo");
        System.clearProperty("rupeecrm.licensing.token");
    }

    @Test
    public void testMissingCredentialsReturnsSafeErrorWithoutCorruptingData() {
        DataProtectionCredentialStore emptyStore = new DataProtectionCredentialStore(emptyConfigFile);
        GitHubStorageProvider provider = new GitHubStorageProvider("crucified1215/rupeecrm-dataprotection-vault", null, emptyStore);

        byte[] payload = "test-encrypted-payload".getBytes();
        BackupStorageProvider.UploadResult result = provider.uploadBackup("TEST_MACHINE_001", payload, null);

        assertNotNull(result);
        assertFalse(result.isSuccess(), "Upload must fail when token is missing");
        assertEquals(401, result.getStatusCode());
        assertEquals(DataProtectionErrorCode.DP_002, result.getErrorCode());
        assertTrue(result.getErrorMessage().contains("Missing Data Protection credentials"));

        byte[] download = provider.downloadBackup("TEST_MACHINE_001");
        assertNull(download, "Download must return null when token is missing");
    }

    @Test
    public void testCredentialIsolationLicenseTokenNotUsedForDataProtection() {
        System.setProperty("rupeecrm.licensing.token", "license-pat-dummy");
        DataProtectionCredentialStore emptyStore = new DataProtectionCredentialStore(emptyConfigFile);
        GitHubStorageProvider provider = new GitHubStorageProvider("crucified1215/rupeecrm-dataprotection-vault", null, emptyStore);

        BackupStorageProvider.UploadResult result = provider.uploadBackup("TEST_MACHINE_001", new byte[]{1, 2, 3}, null);
        assertFalse(result.isSuccess());
        assertEquals(DataProtectionErrorCode.DP_002, result.getErrorCode(), "License token must not authorize Data Protection");
    }

    @Test
    public void testInvalidParametersRejected() {
        DataProtectionCredentialStore emptyStore = new DataProtectionCredentialStore(emptyConfigFile);
        GitHubStorageProvider provider = new GitHubStorageProvider("crucified1215/rupeecrm-dataprotection-vault", "dummy-pat", emptyStore);

        BackupStorageProvider.UploadResult resultNullMachine = provider.uploadBackup(null, new byte[]{1}, null);
        assertFalse(resultNullMachine.isSuccess());
        assertEquals(DataProtectionErrorCode.DP_005, resultNullMachine.getErrorCode());

        BackupStorageProvider.UploadResult resultNullData = provider.uploadBackup("M1", null, null);
        assertFalse(resultNullData.isSuccess());
        assertEquals(DataProtectionErrorCode.DP_005, resultNullData.getErrorCode());

        assertNull(provider.downloadBackup(null));
        assertNull(provider.downloadBackup("   "));
    }

    @Test
    public void testLiveDownloadFromVaultRepository() {
        DataProtectionCredentialStore store = new DataProtectionCredentialStore();
        String token = store.getCurrentToken();
        if (token != null && !token.isBlank()) {
            GitHubStorageProvider provider = new GitHubStorageProvider("crucified1215/rupeecrm-dataprotection-vault", token, store);
            byte[] backup = provider.downloadBackup("WW1Z-AW5M-RTDP-5JK7");
            assertNotNull(backup, "Live backup download must succeed for WW1Z-AW5M-RTDP-5JK7");
            assertTrue(backup.length >= 29, "Downloaded backup must contain valid envelope header");
            assertEquals(0x01, backup[0], "Version byte must be 0x01");
        }
    }
}
