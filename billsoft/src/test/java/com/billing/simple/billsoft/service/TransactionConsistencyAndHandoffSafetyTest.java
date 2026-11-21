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

class TransactionConsistencyAndHandoffSafetyTest {

    @Mock
    private RestTemplate restTemplate;

    private UpdateService service;
    private KeyPair testKeyPair;
    private String testPublicKeyBase64;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        testKeyPair = kpg.generateKeyPair();
        testPublicKeyBase64 = Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded());

        service = new UpdateService(restTemplate);
        service.setExitOnInstall(false);
        ReflectionTestUtils.setField(service, "defaultVersion", "v1.0.340");
        ReflectionTestUtils.setField(service, "publicKeyBase64", testPublicKeyBase64);

        try {
            Files.deleteIfExists(service.getStateFilePath());
            Files.deleteIfExists(service.getHandoffFilePath());
            Path txDir = service.getTxDirectory();
            if (Files.exists(txDir)) {
                try (var stream = Files.list(txDir)) {
                    stream.forEach(f -> {
                        try { Files.deleteIfExists(f); } catch (Exception ignored) {}
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    @Test
    void testStagedUpdateRejectedWhenManifestVersionContradictsState() throws Exception {
        Path txDir = service.getTxDirectory();
        Files.createDirectories(txDir);

        Path stagedWar = txDir.resolve("rupeecrm-update.war");
        Files.writeString(stagedWar, "VALID_BINARY_BYTES");

        Path stateFile = service.getStateFilePath();
        UpdateState state = new UpdateState();
        state.setCurrentVersion("1.0.340");
        state.setTargetVersion("1.0.352");
        state.setStatus(UpdateState.STATUS_READY_TO_INSTALL);
        state.saveAtomic(stateFile);

        // Stale manifest from a previous transaction: v1.0.327
        Files.writeString(txDir.resolve("manifest.json"), "{\"appVersion\":\"1.0.327\",\"artifact\":{\"sha256\":\"309f4ea5...\"}}");

        Map<String, Object> info = service.getStagedUpdateInfo();
        assertFalse(Boolean.TRUE.equals(info.get("stagedUpdateAvailable")),
                "Staged update must be rejected when manifest version does not match transaction target version");
    }

    @Test
    void testInstallStagedUpdateRejectsMismatchedManifestVersion() throws Exception {
        Path txDir = service.getTxDirectory();
        Files.createDirectories(txDir);

        Path stagedWar = txDir.resolve("rupeecrm-update.war");
        Files.writeString(stagedWar, "WAR_CONTENT_123");

        Path stateFile = service.getStateFilePath();
        UpdateState state = new UpdateState();
        state.setCurrentVersion("1.0.340");
        state.setTargetVersion("1.0.352");
        state.setStatus(UpdateState.STATUS_READY_TO_INSTALL);
        state.saveAtomic(stateFile);

        // Stale manifest version 1.0.327
        Files.writeString(txDir.resolve("manifest.json"), "{\"appVersion\":\"1.0.327\"}");
        Files.writeString(txDir.resolve("manifest.sig"), "sig_dummy");

        boolean result = service.installStagedUpdate();
        assertFalse(result, "installStagedUpdate must return false when manifest version contradicts target version");
        assertFalse(Files.exists(service.getHandoffFilePath()), "Handoff file must not be created on validation failure");

        UpdateState reloaded = UpdateState.load(stateFile);
        assertEquals(UpdateState.STATUS_FAILED, reloaded.getStatus());
        assertTrue(reloaded.getErrorMessage().contains("Manifest version mismatch"));
    }

    @Test
    void testInstallStagedUpdateRejectsMismatchedWarSha256() throws Exception {
        Path txDir = service.getTxDirectory();
        Files.createDirectories(txDir);

        Path stagedWar = txDir.resolve("rupeecrm-update.war");
        Files.writeString(stagedWar, "ACTUAL_TAMPERED_WAR_CONTENT");

        String expectedSha256 = "c0d5651c696a5da2e5a421b270ba709a247cc8aba36736791f0dc71fa904151f";

        Path stateFile = service.getStateFilePath();
        UpdateState state = new UpdateState();
        state.setCurrentVersion("1.0.340");
        state.setTargetVersion("1.0.352");
        state.setExpectedSha256(expectedSha256);
        state.setStatus(UpdateState.STATUS_READY_TO_INSTALL);
        state.saveAtomic(stateFile);

        Files.writeString(txDir.resolve("manifest.json"),
                "{\"appVersion\":\"1.0.352\",\"artifact\":{\"sha256\":\"" + expectedSha256 + "\"}}");
        Files.writeString(txDir.resolve("manifest.sig"), "sig_dummy");

        boolean result = service.installStagedUpdate();
        assertFalse(result, "installStagedUpdate must reject when staged WAR hash does not match expected hash");
        assertFalse(Files.exists(service.getHandoffFilePath()), "Handoff file must not be created on hash mismatch");

        UpdateState reloaded = UpdateState.load(stateFile);
        assertEquals(UpdateState.STATUS_VERIFICATION_FAILED, reloaded.getStatus());
        assertTrue(reloaded.getErrorMessage().contains("Staged WAR hash mismatch"));
    }

    @Test
    void testInstallStagedUpdateSucceedsWithVerifiedPackage() throws Exception {
        Path txDir = service.getTxDirectory();
        Files.createDirectories(txDir);

        Path stagedWar = txDir.resolve("rupeecrm-update.war");
        byte[] warBytes = "AUTHENTIC_VALID_WAR_PACKAGE_CONTENT".getBytes(StandardCharsets.UTF_8);
        Files.write(stagedWar, warBytes);

        String warSha256 = UpdateCrypto.computeSha256(stagedWar);

        Path stateFile = service.getStateFilePath();
        UpdateState state = new UpdateState();
        state.setCurrentVersion("1.0.340");
        state.setTargetVersion("1.0.352");
        state.setExpectedSha256(warSha256);
        state.setStatus(UpdateState.STATUS_READY_TO_INSTALL);
        state.saveAtomic(stateFile);

        String manifestJson = "{\"appVersion\":\"1.0.352\",\"artifact\":{\"sha256\":\"" + warSha256 + "\"}}";
        byte[] manifestBytes = manifestJson.getBytes(StandardCharsets.UTF_8);
        String validSig = UpdateCrypto.sign(manifestBytes, testKeyPair.getPrivate());

        Files.write(txDir.resolve("manifest.json"), manifestBytes);
        Files.writeString(txDir.resolve("manifest.sig"), validSig);

        boolean result = service.installStagedUpdate();
        assertTrue(result, "installStagedUpdate must succeed when manifest, hash, and signature are verified");
        assertTrue(Files.exists(service.getHandoffFilePath()), "Handoff file must be created on verified update installation");

        String handoffContent = Files.readString(service.getHandoffFilePath());
        assertTrue(handoffContent.contains("targetVersion=1.0.352"));

        UpdateState reloaded = UpdateState.load(stateFile);
        assertEquals(UpdateState.STATUS_APPLICATION_SHUTDOWN_PENDING, reloaded.getStatus());
    }
}
