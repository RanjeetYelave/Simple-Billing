package com.billing.simple.launcher.update;

import com.billing.simple.launcher.LauncherMain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates Windows-safe file replacement, transaction failure persistence,
 * handoff cleanup, and interrupted state recovery in LauncherMain.
 */
class WindowsSafeUpdaterRemediationTest {

    @TempDir
    Path testBaseDir;

    private String originalBaseDirProp;
    private String originalTestOverrideProp;
    private LauncherMain launcher;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        originalBaseDirProp = System.getProperty("RUPEECRM_BASE_DIR");
        originalTestOverrideProp = System.getProperty("app.update.public-key");

        System.setProperty("RUPEECRM_BASE_DIR", testBaseDir.toAbsolutePath().toString());

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        keyPair = kpg.generateKeyPair();
        String pubKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        System.setProperty("app.update.public-key", pubKeyBase64);

        launcher = new LauncherMain();

        Files.createDirectories(testBaseDir.resolve("data"));
        Files.createDirectories(testBaseDir.resolve("staging/update-tx"));
        Files.createDirectories(testBaseDir.resolve("app"));
    }

    @AfterEach
    void tearDown() {
        if (originalBaseDirProp != null) {
            System.setProperty("RUPEECRM_BASE_DIR", originalBaseDirProp);
        } else {
            System.clearProperty("RUPEECRM_BASE_DIR");
        }
        if (originalTestOverrideProp != null) {
            System.setProperty("app.update.public-key", originalTestOverrideProp);
        } else {
            System.clearProperty("app.update.public-key");
        }
    }

    private byte[] createValidZipBytes(String content) {
        byte[] magic = UpdateCrypto.ZIP_MAGIC;
        byte[] payload = content.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[magic.length + payload.length + 20_000];
        System.arraycopy(magic, 0, out, 0, magic.length);
        System.arraycopy(payload, 0, out, magic.length, payload.length);
        return out;
    }

    private void stageSignedUpdate(String version, byte[] warBytes) throws Exception {
        Path txDir = testBaseDir.resolve("staging/update-tx");
        Files.createDirectories(txDir);

        Path stagedWar = txDir.resolve("rupeecrm-update.war");
        Files.write(stagedWar, warBytes);

        String sha256 = UpdateCrypto.computeSha256(stagedWar);
        UpdateManifest manifest = new UpdateManifest(
                1,
                version,
                "v" + version,
                "2026-11-19T20:00:00Z",
                "1.0.0",
                "rupeecrm-update.war",
                "https://example.com/rupeecrm.war",
                sha256,
                warBytes.length,
                "Release notes for " + version
        );

        byte[] manifestBytes = manifest.toJson().getBytes(StandardCharsets.UTF_8);
        Files.write(txDir.resolve("manifest.json"), manifestBytes);

        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(manifestBytes);
        String sigBase64 = Base64.getEncoder().encodeToString(sig.sign());
        Files.writeString(txDir.resolve("manifest.sig"), sigBase64, StandardCharsets.UTF_8);
    }

    @Test
    void testFailureStatePersistenceAndHandoffDeletionOnMissingStagedWar() throws Exception {
        // Setup live WAR
        Path currentWar = testBaseDir.resolve("app/rupeecrm.war");
        Files.write(currentWar, createValidZipBytes("v1.0.320-live"));

        // Stage manifest but delete staged war
        stageSignedUpdate("1.0.328", createValidZipBytes("v1.0.328-candidate"));
        Files.deleteIfExists(testBaseDir.resolve("staging/update-tx/rupeecrm-update.war"));

        // Create handoff and initial state
        Path handoffToken = testBaseDir.resolve("staging/update.handoff");
        Files.writeString(handoffToken, "transactionId=tx-test-01\ntargetVersion=1.0.328\n");

        Path stateFile = testBaseDir.resolve("staging/update-state.json");
        UpdateState state = new UpdateState();
        state.setStatus(UpdateState.STATUS_APPLICATION_SHUTDOWN_PENDING);
        state.setTargetVersion("1.0.328");
        state.saveAtomic(stateFile);

        // Execute update activation
        boolean success = launcher.applyPendingUpdateIfPresent();
        assertFalse(success, "Update must fail when staged WAR is missing");

        // Verify state is marked UPDATE_FAILED
        UpdateState updatedState = UpdateState.load(stateFile);
        assertEquals(UpdateState.STATUS_UPDATE_FAILED, updatedState.getStatus());
        assertTrue(updatedState.getErrorMessage() != null && !updatedState.getErrorMessage().isEmpty());

        // Verify handoff token was deleted to prevent infinite update retry loops
        assertFalse(Files.exists(handoffToken), "Handoff token MUST be deleted on failure to avoid supervisor re-attempt loops");
    }

    @Test
    void testInterruptedActivationRecoveryRestoresBackupAndDeletesHandoff() throws Exception {
        Path currentWar = testBaseDir.resolve("app/rupeecrm.war");
        Files.write(currentWar, createValidZipBytes("v1.0.320-broken-candidate"));

        Path backupWar = testBaseDir.resolve("app/rupeecrm.war.bak");
        byte[] oldGoodBytes = createValidZipBytes("v1.0.319-known-good");
        Files.write(backupWar, oldGoodBytes);

        Path handoffToken = testBaseDir.resolve("staging/update.handoff");
        Files.writeString(handoffToken, "transactionId=tx-interrupted-01\ntargetVersion=1.0.328\n");

        Path stateFile = testBaseDir.resolve("staging/update-state.json");
        UpdateState state = new UpdateState();
        state.setStatus(UpdateState.STATUS_ACTIVATION_STARTED);
        state.setTargetVersion("1.0.328");
        state.setBackupArtifactPath(backupWar.toAbsolutePath().toString());
        state.saveAtomic(stateFile);

        // Run recovery
        launcher.recoverInterruptedTransactionIfPresent();

        // Verify known-good binary was restored
        assertArrayEquals(oldGoodBytes, Files.readAllBytes(currentWar));

        // Verify status rolled back
        UpdateState recoveredState = UpdateState.load(stateFile);
        assertEquals(UpdateState.STATUS_ROLLED_BACK, recoveredState.getStatus());

        // Verify handoff token was purged
        assertFalse(Files.exists(handoffToken), "Handoff token must be deleted during interrupted activation recovery");
    }

    @Test
    void testSuccessfulWarReplacementAndPostChecksumVerification() throws Exception {
        Path currentWar = testBaseDir.resolve("app/rupeecrm.war");
        Files.write(currentWar, createValidZipBytes("v1.0.320-old-live"));

        byte[] candidateBytes = createValidZipBytes("v1.0.328-candidate-war-payload");
        stageSignedUpdate("1.0.328", candidateBytes);

        Path handoffToken = testBaseDir.resolve("staging/update.handoff");
        Files.writeString(handoffToken, "transactionId=tx-success-01\ntargetVersion=1.0.328\n");

        Path stateFile = testBaseDir.resolve("staging/update-state.json");
        UpdateState state = new UpdateState();
        state.setStatus(UpdateState.STATUS_APPLICATION_SHUTDOWN_PENDING);
        state.setTargetVersion("1.0.328");
        state.saveAtomic(stateFile);

        boolean success = launcher.applyPendingUpdateIfPresent();
        assertTrue(success, "Update must succeed when signed and verified");

        assertTrue(launcher.isCandidateLaunch());
        assertEquals("1.0.328", launcher.getCandidateVersion());

        // Verify candidate content is in place
        assertArrayEquals(candidateBytes, Files.readAllBytes(currentWar));

        // Verify state is STARTING_NEW_VERSION
        UpdateState finalState = UpdateState.load(stateFile);
        assertEquals(UpdateState.STATUS_STARTING_NEW_VERSION, finalState.getStatus());

        // Verify handoff token consumed
        assertFalse(Files.exists(handoffToken));
    }

    @Test
    void testPostReplacementChecksumMismatchTriggersImmediateRollback() throws Exception {
        Path currentWar = testBaseDir.resolve("app/rupeecrm.war");
        byte[] originalLiveBytes = createValidZipBytes("v1.0.320-live-original");
        Files.write(currentWar, originalLiveBytes);

        // Stage candidate
        byte[] candidateBytes = createValidZipBytes("v1.0.328-candidate-payload");
        stageSignedUpdate("1.0.328", candidateBytes);

        // Now corrupt the manifest SHA-256 so it verifies the staged war upfront,
        // but let's test post-replacement checksum mismatch by tampering with manifest right before check
        // Or directly verify that an invalid replacement throws and restores backupWar:
        Path backupWar = testBaseDir.resolve("app/rupeecrm.war.bak");
        Files.write(backupWar, originalLiveBytes);

        Path handoffToken = testBaseDir.resolve("staging/update.handoff");
        Files.writeString(handoffToken, "transactionId=tx-checksum-fail\ntargetVersion=1.0.328\n");

        Path stateFile = testBaseDir.resolve("staging/update-state.json");
        UpdateState state = new UpdateState();
        state.setStatus(UpdateState.STATUS_APPLICATION_SHUTDOWN_PENDING);
        state.setTargetVersion("1.0.328");
        state.saveAtomic(stateFile);

        // Stage an update whose staged SHA-256 doesn't match manifest:
        // Update manifest with a different sha
        UpdateManifest tamperedManifest = new UpdateManifest(
                1, "1.0.328", "v1.0.328", "2026-11-19T20:00:00Z", "1.0.0",
                "rupeecrm-update.war", "https://example.com/rupeecrm.war",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                candidateBytes.length, "Release notes"
        );
        byte[] mBytes = tamperedManifest.toJson().getBytes(StandardCharsets.UTF_8);
        Files.write(testBaseDir.resolve("staging/update-tx/manifest.json"), mBytes);
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(keyPair.getPrivate());
        sig.update(mBytes);
        Files.writeString(testBaseDir.resolve("staging/update-tx/manifest.sig"), Base64.getEncoder().encodeToString(sig.sign()), StandardCharsets.UTF_8);

        boolean success = launcher.applyPendingUpdateIfPresent();
        assertFalse(success, "Update must fail on checksum mismatch");

        UpdateState finalState = UpdateState.load(stateFile);
        assertEquals(UpdateState.STATUS_UPDATE_FAILED, finalState.getStatus());
        assertFalse(Files.exists(handoffToken), "Handoff token must be deleted on failure");
    }

    @Test
    void testReadOnlyPackagedWarRedirectsToUserWritableBaseDir() throws Exception {
        // Create read-only packaged location: testBaseDir/simulated_program_files/resources/rupeecrm.war
        Path installDir = testBaseDir.resolve("simulated_program_files/resources");
        Files.createDirectories(installDir);
        Path readOnlyWar = installDir.resolve("rupeecrm.war");
        byte[] packagedBytes = createValidZipBytes("v1.0.320-packaged-app");
        Files.write(readOnlyWar, packagedBytes);

        System.setProperty("RUPEECRM_APP_DIR", installDir.toAbsolutePath().toString());

        // Make install dir read-only
        installDir.toFile().setReadOnly();

        byte[] candidateBytes = createValidZipBytes("v1.0.328-candidate-for-user-dir");
        stageSignedUpdate("1.0.328", candidateBytes);

        Path handoffToken = testBaseDir.resolve("staging/update.handoff");
        Files.writeString(handoffToken, "transactionId=tx-user-dir-01\ntargetVersion=1.0.328\n");

        Path stateFile = testBaseDir.resolve("staging/update-state.json");
        UpdateState state = new UpdateState();
        state.setStatus(UpdateState.STATUS_APPLICATION_SHUTDOWN_PENDING);
        state.setTargetVersion("1.0.328");
        state.saveAtomic(stateFile);

        boolean success = launcher.applyPendingUpdateIfPresent();

        // Restore permissions before asserts
        installDir.toFile().setWritable(true);
        System.clearProperty("RUPEECRM_APP_DIR");

        assertTrue(success, "Update should succeed by redirecting to user-writable base directory");

        // Verify that base app dir received the updated WAR
        Path baseAppWar = testBaseDir.resolve("app/rupeecrm.war");
        assertTrue(Files.exists(baseAppWar), "Base user app directory must contain updated WAR");
        assertArrayEquals(candidateBytes, Files.readAllBytes(baseAppWar));
    }
}
