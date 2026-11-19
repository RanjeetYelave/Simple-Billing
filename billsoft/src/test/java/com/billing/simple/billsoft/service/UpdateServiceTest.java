package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.update.UpdateCrypto;
import com.billing.simple.billsoft.update.UpdateManifest;
import com.billing.simple.billsoft.update.UpdateState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class UpdateServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private UpdateService service;
    private KeyPair testKeyPair;
    private String testPublicKeyBase64;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        testKeyPair = kpg.generateKeyPair();
        testPublicKeyBase64 = Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded());

        service = new UpdateService(restTemplate);
        service.setExitOnInstall(false);
        ReflectionTestUtils.setField(service, "defaultVersion", "v1.0.0");
        ReflectionTestUtils.setField(service, "manifestUrl", "https://github.com/RanjeetYelave/Simple-Billing/releases/latest/download/manifest.json");
        ReflectionTestUtils.setField(service, "signatureUrl", "https://github.com/RanjeetYelave/Simple-Billing/releases/latest/download/manifest.sig");
        ReflectionTestUtils.setField(service, "publicKeyBase64", testPublicKeyBase64);

        try {
            Files.deleteIfExists(service.getStateFilePath());
            Files.deleteIfExists(service.getHandoffFilePath());
        } catch (Exception ignored) {}
    }

    private void mockManifestAndSignature(UpdateManifest manifest) throws Exception {
        byte[] manifestBytes = manifest.toJson().getBytes(StandardCharsets.UTF_8);
        String signature = UpdateCrypto.sign(manifestBytes, testKeyPair.getPrivate());

        when(restTemplate.getForObject(contains("manifest.json"), eq(byte[].class)))
                .thenReturn(manifestBytes);
        when(restTemplate.getForObject(contains("manifest.sig"), eq(String.class)))
                .thenReturn(signature);
    }

    @Test
    void testCheckUpdateValidSignedManifest() throws Exception {
        UpdateManifest manifest = new UpdateManifest(
                1, "1.1.0", "v1.1.0", "2026-10-19T14:00:00Z", "1.0.0",
                "rupeecrm.war", "https://example.com/rupeecrm.war",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                25_000_000L, "• Added Smart Reorder\n• PDF generation fixes"
        );
        mockManifestAndSignature(manifest);

        Map<String, Object> result = service.checkUpdate();
        assertNotNull(result);
        assertEquals("1.1.0", result.get("latestVersion"));
        assertTrue((Boolean) result.get("updateAvailable"));
        assertTrue(((String) result.get("releaseNotes")).contains("Smart Reorder"));
    }

    @Test
    void testCheckUpdateTamperedSignatureFails() throws Exception {
        UpdateManifest manifest = new UpdateManifest(
                1, "1.1.0", "v1.1.0", "2026-10-19T14:00:00Z", "1.0.0",
                "rupeecrm.war", "https://example.com/rupeecrm.war",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                25_000_000L, "Valid notes"
        );
        byte[] manifestBytes = manifest.toJson().getBytes(StandardCharsets.UTF_8);

        // Sign with a DIFFERENT ephemeral keypair so signature verification fails against testPublicKeyBase64
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair attackerKey = kpg.generateKeyPair();
        String fakeSignature = UpdateCrypto.sign(manifestBytes, attackerKey.getPrivate());

        when(restTemplate.getForObject(contains("manifest.json"), eq(byte[].class)))
                .thenReturn(manifestBytes);
        when(restTemplate.getForObject(contains("manifest.sig"), eq(String.class)))
                .thenReturn(fakeSignature);

        Map<String, Object> result = service.checkUpdate();
        assertNotNull(result);
        assertFalse((Boolean) result.get("updateAvailable"));
        assertTrue(result.containsKey("error"));
        assertTrue(((String) result.get("error")).toLowerCase().contains("verification failed") ||
                   ((String) result.get("error")).toLowerCase().contains("signature"));
    }

    @Test
    void testCheckUpdateNetworkOrRateLimitFailureReturnsError() {
        when(restTemplate.getForObject(contains("manifest.json"), eq(byte[].class)))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "API rate limit exceeded"));

        Map<String, Object> result = service.checkUpdate(true);
        assertNotNull(result);
        assertFalse((Boolean) result.get("updateAvailable"));
        assertTrue(result.containsKey("error"));
        assertTrue(((String) result.get("error")).contains("rate limit exceeded") ||
                   ((String) result.get("error")).toLowerCase().contains("failed"));
    }

    @Test
    void testCheckUpdateNoNewerInstalledEqualsLatest() throws Exception {
        UpdateManifest manifest = new UpdateManifest(
                1, "1.0.0", "v1.0.0", "2026-10-19T14:00:00Z", "1.0.0",
                "rupeecrm.war", "https://example.com/rupeecrm.war",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                25_000_000L, "Initial release"
        );
        mockManifestAndSignature(manifest);

        Map<String, Object> result = service.checkUpdate();
        assertNotNull(result);
        assertFalse((Boolean) result.get("updateAvailable"));
        assertEquals("1.0.0", result.get("latestVersion"));
    }

    @Test
    void testCheckUpdateDowngradePrevention() throws Exception {
        UpdateManifest manifest = new UpdateManifest(
                1, "0.9.5", "v0.9.5", "2026-10-19T14:00:00Z", "1.0.0",
                "rupeecrm.war", "https://example.com/rupeecrm.war",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                25_000_000L, "Older release"
        );
        mockManifestAndSignature(manifest);

        Map<String, Object> result = service.checkUpdate();
        assertNotNull(result);
        assertFalse((Boolean) result.get("updateAvailable"));
    }

    @Test
    void testCheckUpdateCacheHit() throws Exception {
        UpdateManifest manifest = new UpdateManifest(
                1, "1.2.0", "v1.2.0", "2026-10-19T14:00:00Z", "1.0.0",
                "rupeecrm.war", "https://example.com/rupeecrm.war",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                25_000_000L, "Notes"
        );
        mockManifestAndSignature(manifest);

        // First call
        Map<String, Object> first = service.checkUpdate(false);
        assertEquals("1.2.0", first.get("latestVersion"));

        // Second call within cache window: uses cache
        Map<String, Object> second = service.checkUpdate(false);
        assertEquals("1.2.0", second.get("latestVersion"));

        org.mockito.Mockito.verify(restTemplate, org.mockito.Mockito.times(1))
                .getForObject(contains("manifest.json"), eq(byte[].class));
    }

    @Test
    void testCheckUpdateManualForceRefreshBypassesFreshCache() throws Exception {
        UpdateManifest manifest = new UpdateManifest(
                1, "1.2.0", "v1.2.0", "2026-10-19T14:00:00Z", "1.0.0",
                "rupeecrm.war", "https://example.com/rupeecrm.war",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                25_000_000L, "Notes"
        );
        mockManifestAndSignature(manifest);

        service.checkUpdate(false);
        service.checkUpdate(true);

        org.mockito.Mockito.verify(restTemplate, org.mockito.Mockito.times(2))
                .getForObject(contains("manifest.json"), eq(byte[].class));
    }

    @Test
    void testCompareVersionsSemantics() {
        assertTrue(UpdateService.compareVersions("v1.0.10", "v1.0.9") > 0);
        assertTrue(UpdateService.compareVersions("v1.1.0", "v1.0.9") > 0);
        assertTrue(UpdateService.compareVersions("2.0.0", "1.9.99") > 0);
        assertTrue(UpdateService.compareVersions("v1.0.0", "1.0.0") == 0);
        assertTrue(UpdateService.compareVersions("v1.2", "v1.2.0") == 0);
        assertTrue(UpdateService.compareVersions("v1.0.1", "v1.0.2") < 0);
        assertTrue(UpdateService.compareVersions("v1.0.0", "v1.0.0-SNAPSHOT") > 0);
        assertTrue(UpdateService.compareVersions("v1.0.0-SNAPSHOT", "v1.0.0") < 0);
    }

    @Test
    void testExtractCustomerChangelog() {
        String fullReleaseBody = "Added Smart Reorder with multi-vendor PO support.";
        String extracted = UpdateService.extractCustomerChangelog(fullReleaseBody);
        assertEquals("Added Smart Reorder with multi-vendor PO support.", extracted);
        assertEquals("", UpdateService.extractCustomerChangelog(""));
        assertEquals("", UpdateService.extractCustomerChangelog(null));
    }

    @Test
    void testCheckForUpdateCompleteSuccessReporting() throws Exception {
        Path stateFile = service.getStateFilePath();
        Files.createDirectories(stateFile.getParent());

        UpdateState state = new UpdateState();
        state.setStatus(UpdateState.STATUS_COMMITTED);
        state.setCurrentVersion("1.0.0");
        state.setTargetVersion("1.1.0");
        state.saveAtomic(stateFile);

        Map<String, Object> complete = service.checkForUpdateComplete();
        assertTrue((Boolean) complete.get("justUpdated"));
        assertEquals("1.1.0", complete.get("updatedVersion"));

        // Version should now report updated version
        assertEquals("1.1.0", service.getCurrentVersion());
    }

    @Test
    void testCheckForUpdateCompleteRollbackReporting() throws Exception {
        Path stateFile = service.getStateFilePath();
        Files.createDirectories(stateFile.getParent());

        UpdateState state = new UpdateState();
        state.setStatus(UpdateState.STATUS_ROLLED_BACK);
        state.setCurrentVersion("1.0.0");
        state.setTargetVersion("1.1.0");
        state.setErrorMessage("Health check timeout after update");
        state.saveAtomic(stateFile);

        Map<String, Object> complete = service.checkForUpdateComplete();
        assertFalse((Boolean) complete.get("justUpdated"));
        assertTrue((Boolean) complete.get("rolledBack"));
        assertTrue((Boolean) complete.get("justRolledBack"));
        assertEquals("1.1.0", complete.get("failedVersion"));
        assertEquals("1.0.0", complete.get("currentVersion"));
        assertEquals("Health check timeout after update", complete.get("errorMessage"));

        // Current version remains original version
        assertEquals("1.0.0", service.getCurrentVersion());
    }

    @Test
    void testDownloadArtifactToPartDeletesPartFileOnFailure() throws Exception {
        Path destWar = tempDir.resolve("rupeecrm-update.war");
        Path partWar = tempDir.resolve("rupeecrm-update.war.part");

        // Attempting to download from invalid or unreachable URL throws exception and cleans up .part file
        assertThrows(Exception.class, () -> {
            service.downloadArtifactToPart("https://127.0.0.1:9/non-existent-artifact.war", partWar, 1000L, "1.1.0");
        });

        assertFalse(Files.exists(partWar), "The .part temporary download file must be cleaned up on failure");
        assertFalse(Files.exists(destWar), "The target destination file must not exist if download failed");
    }

    @Test
    void testCancelUpdateWhenNoUpdateRunning() {
        assertFalse(service.cancelUpdate());
    }

    @Test
    void testCheckForUpdateCompleteVersionMismatchReporting() throws Exception {
        Path stateFile = service.getStateFilePath();
        Files.createDirectories(stateFile.getParent());

        UpdateState state = new UpdateState();
        state.setStatus(UpdateState.STATUS_VERSION_MISMATCH);
        state.setCurrentVersion("1.0.0");
        state.setTargetVersion("1.1.0");
        state.setErrorMessage("Running version 1.0.0 does not match target candidate version 1.1.0");
        state.saveAtomic(stateFile);

        Map<String, Object> complete = service.checkForUpdateComplete();
        assertFalse((Boolean) complete.get("justUpdated"));
        assertTrue((Boolean) complete.get("rolledBack"));
        assertTrue((Boolean) complete.get("justRolledBack"));
        assertEquals("1.1.0", complete.get("failedVersion"));
        assertEquals("1.0.0", complete.get("currentVersion"));
        assertTrue(((String) complete.get("errorMessage")).contains("does not match target"));
    }

    @Test
    void testBillsoftVersionMetadataOverride() throws Exception {
        System.setProperty("app.version.allow-mock-override", "true");
        Path overrideFile = Path.of(".billsoft-version");
        try {
            Files.writeString(overrideFile, "v0.9.0");
            assertEquals("v0.9.0", service.getCurrentVersion());
        } finally {
            System.clearProperty("app.version.allow-mock-override");
            Files.deleteIfExists(overrideFile);
        }
    }

    @Test
    void testStaleBillsoftVersionPurgedWhenRunningBinaryIsAuthoritative() throws Exception {
        service.setDefaultVersion("v1.0.329");
        Path overrideFile = Path.of(".billsoft-version");
        try {
            Files.writeString(overrideFile, "v1.0.320");
            // Running binary v1.0.329 is authoritative and must purge stale v1.0.320 file
            assertEquals("v1.0.329", service.getCurrentVersion());
            assertFalse(Files.exists(overrideFile), "Stale .billsoft-version file must be purged");
        } finally {
            Files.deleteIfExists(overrideFile);
        }
    }

    @Test
    void testStaleUpdateStateIgnoredWhenRunningBinaryIsAuthoritative() throws Exception {
        service.setDefaultVersion("v1.0.308");
        Path stateFile = service.getStateFilePath();
        Files.createDirectories(stateFile.getParent());

        UpdateState staleState = new UpdateState();
        staleState.setStatus(UpdateState.STATUS_COMMITTED);
        staleState.setCurrentVersion("1.0.280");
        staleState.setTargetVersion("1.0.280");
        staleState.saveAtomic(stateFile);

        // Even though update-state.json says 1.0.280, the running binary version v1.0.308 must win
        assertEquals("v1.0.308", service.getCurrentVersion());

        // And update-state.json must have been reconciled to v1.0.308
        UpdateState reconciled = UpdateState.load(stateFile);
        assertNotNull(reconciled);
        assertEquals("v1.0.308", reconciled.getCurrentVersion());
    }

    @Test
    void testStaleTargetVersionDoesNotOverrideAuthoritativeVersion() throws Exception {
        service.setDefaultVersion("v1.0.280");
        Path stateFile = service.getStateFilePath();
        Files.createDirectories(stateFile.getParent());

        UpdateState staleState = new UpdateState();
        staleState.setStatus(UpdateState.STATUS_COMMITTED);
        staleState.setCurrentVersion("1.0.250");
        staleState.setTargetVersion("1.0.308");
        staleState.saveAtomic(stateFile);

        // Running binary is actually 1.0.280, targetVersion 1.0.308 must not trick system
        assertEquals("v1.0.280", service.getCurrentVersion());
    }

    @Test
    void testStagedUpdateRecognizedWhenReadyToInstall() throws Exception {
        service.setDefaultVersion("v1.0.320");
        Path txDir = service.getTxDirectory();
        Files.createDirectories(txDir);
        Path stagedWar = txDir.resolve("rupeecrm-update.war");
        Files.writeString(stagedWar, "PK-STAGED-WAR-BYTES-327");

        Path stateFile = service.getStateFilePath();
        UpdateState state = new UpdateState();
        state.setCurrentVersion("1.0.320");
        state.setTargetVersion("1.0.327");
        state.setStatus(UpdateState.STATUS_READY_TO_INSTALL);
        state.saveAtomic(stateFile);

        Map<String, Object> stagedInfo = service.getStagedUpdateInfo();
        assertTrue(Boolean.TRUE.equals(stagedInfo.get("stagedUpdateAvailable")));
        assertEquals("1.0.327", stagedInfo.get("stagedVersion"));
        assertEquals(UpdateState.STATUS_READY_TO_INSTALL, stagedInfo.get("stagedStatus"));

        // When checking update, response must include staged metadata
        Map<String, Object> check = service.checkUpdate(true);
        assertTrue(Boolean.TRUE.equals(check.get("stagedUpdateAvailable")));
        assertEquals("1.0.327", check.get("stagedVersion"));
        assertFalse(Boolean.TRUE.equals(check.get("hasNewerRelease")));
    }

    @Test
    void testCheckUpdateDetectsNewerReleaseWhenOlderStaged() throws Exception {
        service.setDefaultVersion("v1.0.320");
        Path txDir = service.getTxDirectory();
        Files.createDirectories(txDir);
        Path stagedWar = txDir.resolve("rupeecrm-update.war");
        Files.writeString(stagedWar, "PK-STAGED-WAR-BYTES-327");

        Path stateFile = service.getStateFilePath();
        UpdateState state = new UpdateState();
        state.setCurrentVersion("1.0.320");
        state.setTargetVersion("1.0.327");
        state.setStatus(UpdateState.STATUS_READY_TO_INSTALL);
        state.saveAtomic(stateFile);

        // Mock remote manifest with 1.0.328 (newer than 1.0.327)
        UpdateManifest manifest328 = new UpdateManifest(
                1, "1.0.328", "v1.0.328", "2026-11-20T00:00:00Z", "1.0.0",
                "rupeecrm-1.0.328.war", "https://github.com/RanjeetYelave/Simple-Billing/releases/download/v1.0.328/rupeecrm.war",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                25_000_000L, "Release notes for 328"
        );
        mockManifestAndSignature(manifest328);

        Map<String, Object> check = service.checkUpdate(true);
        assertTrue(Boolean.TRUE.equals(check.get("stagedUpdateAvailable")));
        assertEquals("1.0.327", check.get("stagedVersion"));
        assertEquals("1.0.328", check.get("latestVersion"));
        assertTrue(Boolean.TRUE.equals(check.get("hasNewerRelease")));
        assertTrue(Boolean.TRUE.equals(check.get("updateAvailable")));
    }

    @Test
    void testApplyUpdateDirectlyInstallsStagedWithoutRedownload() throws Exception {
        service.setDefaultVersion("v1.0.320");
        Path txDir = service.getTxDirectory();
        Files.createDirectories(txDir);
        Path stagedWar = txDir.resolve("rupeecrm-update.war");
        Files.writeString(stagedWar, "PK-STAGED-WAR-BYTES-327");

        Path stateFile = service.getStateFilePath();
        UpdateState state = new UpdateState();
        state.setCurrentVersion("1.0.320");
        state.setTargetVersion("1.0.327");
        state.setStatus(UpdateState.STATUS_READY_TO_INSTALL);
        state.saveAtomic(stateFile);

        Files.writeString(txDir.resolve("manifest.json"), "{\"appVersion\":\"1.0.327\"}");
        Files.writeString(txDir.resolve("manifest.sig"), "sig327");

        // Calling applyUpdate without version or with "1.0.327" directly installs staged update
        boolean applied = service.applyUpdate("1.0.327");
        assertTrue(applied);
        assertTrue(Files.exists(service.getHandoffFilePath()));
    }

    @Test
    void testSafeStagedReplacementPreservesStagedPackageOnFailure() throws Exception {
        service.setDefaultVersion("v1.0.320");
        Path txDir = service.getTxDirectory();
        Files.createDirectories(txDir);
        Path stagedWar = txDir.resolve("rupeecrm-update.war");
        String originalContent = "PK-BUILD-327-SAFE-CONTENT";
        Files.writeString(stagedWar, originalContent);

        Path stateFile = service.getStateFilePath();
        UpdateState state = new UpdateState();
        state.setCurrentVersion("1.0.320");
        state.setTargetVersion("1.0.327");
        state.setStatus(UpdateState.STATUS_READY_TO_INSTALL);
        state.saveAtomic(stateFile);

        // Newer version 328 manifest
        UpdateManifest manifest328 = new UpdateManifest(
                1, "1.0.328", "v1.0.328", "2026-11-20T00:00:00Z", "1.0.0",
                "rupeecrm-1.0.328.war", "https://invalid-download-host-999.example.com/rupeecrm.war",
                "0000000000000000000000000000000000000000000000000000000000000000",
                25_000_000L, "Release notes for 328"
        );

        // Invoke pipeline directly via reflection to simulate newer release failure
        java.lang.reflect.Method method = UpdateService.class.getDeclaredMethod("executeUpdatePipeline", UpdateManifest.class);
        method.setAccessible(true);
        method.invoke(service, manifest328);

        // CRITICAL SAFETY ASSERTION:
        // Build 327 WAR must STILL exist and must contain the exact original content!
        assertTrue(Files.exists(stagedWar));
        assertEquals(originalContent, Files.readString(stagedWar));

        // And update-state.json must have preserved / restored READY_TO_INSTALL for 1.0.327
        UpdateState preservedState = UpdateState.load(stateFile);
        assertEquals(UpdateState.STATUS_READY_TO_INSTALL, preservedState.getStatus());
        assertEquals("1.0.327", preservedState.getTargetVersion());
    }

    @Test
    void testRemindLaterPreservesDeferredState() throws Exception {
        service.setDefaultVersion("v1.0.320");
        Path txDir = service.getTxDirectory();
        Files.createDirectories(txDir);
        Path stagedWar = txDir.resolve("rupeecrm-update.war");
        Files.writeString(stagedWar, "PK-STAGED-WAR-327");

        Path stateFile = service.getStateFilePath();
        UpdateState state = new UpdateState();
        state.setCurrentVersion("1.0.320");
        state.setTargetVersion("1.0.327");
        state.setStatus(UpdateState.STATUS_READY_TO_INSTALL);
        state.saveAtomic(stateFile);

        boolean deferred = service.remindLater();
        assertTrue(deferred);

        UpdateState loaded = UpdateState.load(stateFile);
        assertEquals(UpdateState.STATUS_READY_TO_INSTALL, loaded.getStatus());
        assertEquals("1.0.327", loaded.getTargetVersion());
    }

    private Path createMockOtaZip(String appVersion, byte[] warBytes, boolean validSig, boolean validChecksum, String zipSlipEntry) throws Exception {
        Path zipFile = tempDir.resolve("test-update-" + UUID.randomUUID() + ".zip");
        String warSha256 = UpdateCrypto.computeSha256(warBytes);
        String manifestSha256 = validChecksum ? warSha256 : "0000000000000000000000000000000000000000000000000000000000000000";

        UpdateManifest manifest = new UpdateManifest(
                1, appVersion, "v" + appVersion, "2026-11-20T00:00:00Z", "1.0.0",
                "rupeecrm.war", "https://github.com/RanjeetYelave/Simple-Billing/releases/download/v" + appVersion + "/rupeecrm.war",
                manifestSha256, warBytes.length, "Release notes for " + appVersion
        );

        byte[] manifestBytes = manifest.toJson().getBytes(StandardCharsets.UTF_8);
        String sig = validSig ? UpdateCrypto.sign(manifestBytes, testKeyPair.getPrivate()) : "INVALID_BASE64_SIGNATURE_HERE==";

        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zipFile))) {
            // manifest.json
            zos.putNextEntry(new java.util.zip.ZipEntry("manifest.json"));
            zos.write(manifestBytes);
            zos.closeEntry();

            // manifest.sig
            zos.putNextEntry(new java.util.zip.ZipEntry("manifest.sig"));
            zos.write(sig.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // rupeecrm.war
            zos.putNextEntry(new java.util.zip.ZipEntry("rupeecrm.war"));
            zos.write(warBytes);
            zos.closeEntry();

            if (zipSlipEntry != null) {
                zos.putNextEntry(new java.util.zip.ZipEntry(zipSlipEntry));
                zos.write("ATTACK".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return zipFile;
    }

    private byte[] createDummyWarBytes(int size) {
        byte[] bytes = new byte[Math.max(size, 20_000)];
        // ZIP magic bytes
        bytes[0] = 0x50; bytes[1] = 0x4b; bytes[2] = 0x03; bytes[3] = 0x04;
        for (int i = 4; i < bytes.length; i++) {
            bytes[i] = (byte) (i % 127);
        }
        return bytes;
    }

    @Test
    void testStageManualUpdatePackageValidOfflinePackage() throws Exception {
        service.setDefaultVersion("v1.0.320");
        byte[] warBytes = createDummyWarBytes(25_000);
        Path zipFile = createMockOtaZip("1.0.350", warBytes, true, true, null);

        Map<String, Object> result = service.stageManualUpdatePackage(zipFile, false);
        assertNotNull(result);
        assertEquals("success", result.get("status"));
        assertEquals("1.0.350", result.get("stagedVersion"));
        assertTrue((Boolean) result.get("stagedUpdateAvailable"));

        // Verify staged files in txDir
        Path txDir = service.getTxDirectory();
        Path stagedWar = txDir.resolve("rupeecrm-update.war");
        assertTrue(Files.exists(stagedWar));
        assertEquals(warBytes.length, Files.size(stagedWar));
        assertEquals(UpdateCrypto.computeSha256(warBytes), UpdateCrypto.computeSha256(stagedWar));

        // Verify state is READY_TO_INSTALL
        UpdateState state = UpdateState.load(service.getStateFilePath());
        assertEquals(UpdateState.STATUS_READY_TO_INSTALL, state.getStatus());
        assertEquals("1.0.350", state.getTargetVersion());
    }

    @Test
    void testStageManualUpdatePackageInvalidSignatureRejected() throws Exception {
        service.setDefaultVersion("v1.0.320");
        byte[] warBytes = createDummyWarBytes(25_000);
        Path zipFile = createMockOtaZip("1.0.350", warBytes, false, true, null);

        assertThrows(java.security.GeneralSecurityException.class, () -> {
            service.stageManualUpdatePackage(zipFile, false);
        });
    }

    @Test
    void testStageManualUpdatePackageChecksumMismatchRejected() throws Exception {
        service.setDefaultVersion("v1.0.320");
        byte[] warBytes = createDummyWarBytes(25_000);
        Path zipFile = createMockOtaZip("1.0.350", warBytes, true, false, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            service.stageManualUpdatePackage(zipFile, false);
        });
        assertTrue(ex.getMessage().contains("checksum mismatch") || ex.getMessage().contains("SHA-256"));
    }

    @Test
    void testStageManualUpdatePackageZipSlipRejected() throws Exception {
        service.setDefaultVersion("v1.0.320");
        byte[] warBytes = createDummyWarBytes(25_000);
        Path zipFile = createMockOtaZip("1.0.350", warBytes, true, true, "../../malicious.txt");

        SecurityException ex = assertThrows(SecurityException.class, () -> {
            service.stageManualUpdatePackage(zipFile, false);
        });
        assertTrue(ex.getMessage().contains("Zip Slip"));
    }

    @Test
    void testStageManualUpdatePackageSameVersionReported() throws Exception {
        service.setDefaultVersion("v1.0.320");
        byte[] warBytes = createDummyWarBytes(25_000);
        Path zipFile = createMockOtaZip("1.0.320", warBytes, true, true, null);

        Map<String, Object> result = service.stageManualUpdatePackage(zipFile, false);
        assertEquals("already_installed", result.get("status"));
        assertTrue(result.get("message").toString().contains("already installed"));
    }

    @Test
    void testStageManualUpdatePackageDowngradeRejected() throws Exception {
        service.setDefaultVersion("v1.0.320");
        byte[] warBytes = createDummyWarBytes(25_000);
        Path zipFile = createMockOtaZip("1.0.280", warBytes, true, true, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            service.stageManualUpdatePackage(zipFile, false);
        });
        assertTrue(ex.getMessage().contains("Downgrade rejected"));
    }

    @Test
    void testStageManualUpdatePackageReplacesExistingStagedWhenConfirmed() throws Exception {
        service.setDefaultVersion("v1.0.320");
        Path txDir = service.getTxDirectory();
        Files.createDirectories(txDir);
        Files.writeString(txDir.resolve("rupeecrm-update.war"), "OLD-327-WAR");
        UpdateState state = new UpdateState();
        state.setCurrentVersion("1.0.320");
        state.setTargetVersion("1.0.327");
        state.setStatus(UpdateState.STATUS_READY_TO_INSTALL);
        state.saveAtomic(service.getStateFilePath());

        byte[] newWarBytes = createDummyWarBytes(25_000);
        Path zipFile = createMockOtaZip("1.0.335", newWarBytes, true, true, null);

        // When confirmReplace is false, returns confirmation prompt
        Map<String, Object> conflict = service.stageManualUpdatePackage(zipFile, false);
        assertEquals("confirm_required", conflict.get("status"));
        assertEquals("1.0.327", conflict.get("existingStagedVersion"));
        assertEquals("1.0.335", conflict.get("newVersion"));

        // When confirmReplace is true, staging succeeds and updates targetVersion
        Map<String, Object> success = service.stageManualUpdatePackage(zipFile, true);
        assertEquals("success", success.get("status"));
        assertEquals("1.0.335", success.get("stagedVersion"));

        UpdateState updatedState = UpdateState.load(service.getStateFilePath());
        assertEquals("1.0.335", updatedState.getTargetVersion());
    }
}
