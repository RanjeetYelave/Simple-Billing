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
        ReflectionTestUtils.setField(service, "defaultVersion", "v1.0.0");
        ReflectionTestUtils.setField(service, "manifestUrl", "https://github.com/RanjeetYelave/Simple-Billing/releases/latest/download/manifest.json");
        ReflectionTestUtils.setField(service, "signatureUrl", "https://github.com/RanjeetYelave/Simple-Billing/releases/latest/download/manifest.sig");
        ReflectionTestUtils.setField(service, "publicKeyBase64", testPublicKeyBase64);
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
}
