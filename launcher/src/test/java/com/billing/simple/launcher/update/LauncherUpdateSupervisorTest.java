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
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class LauncherUpdateSupervisorTest {

    @TempDir
    Path testBaseDir;

    private String originalBaseDirProp;
    private LauncherMain launcher;
    private KeyPair pinnedKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        originalBaseDirProp = System.getProperty("RUPEECRM_BASE_DIR");
        System.setProperty("RUPEECRM_BASE_DIR", testBaseDir.toAbsolutePath().toString());

        launcher = new LauncherMain();

        // Create directory layout: data, staging, update-tx, app
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
    }

    private File createLiveWar(String content) throws Exception {
        Path warPath = testBaseDir.resolve("app/rupeecrm.war");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        Files.write(warPath, bytes);
        return warPath.toFile();
    }

    private byte[] createValidWarBytes(String payload) {
        byte[] magic = UpdateCrypto.ZIP_MAGIC;
        byte[] extra = payload.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[magic.length + extra.length + 20_000]; // Pad to exceed 10KB minimum size
        System.arraycopy(magic, 0, result, 0, magic.length);
        System.arraycopy(extra, 0, result, magic.length, extra.length);
        return result;
    }

    @Test
    void testNoHandoffTokenIgnoresStagedWar() throws Exception {
        File currentWar = createLiveWar("live-v1.0.0-content");
        Path staged = testBaseDir.resolve("staging/update-tx/rupeecrm-update.war");
        Files.write(staged, createValidWarBytes("staged-content"));

        // No update.handoff exists
        boolean activated = launcher.applyPendingUpdateIfPresent();
        assertFalse(activated, "Must not activate update without valid handoff token");

        // Live binary must remain unchanged
        assertEquals("live-v1.0.0-content", Files.readString(currentWar.toPath()));
    }

    @Test
    void testUntrustedSignatureRejectsActivation() throws Exception {
        File currentWar = createLiveWar("live-v1.0.0-content");

        // Create an untrusted keypair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair untrustedKey = kpg.generateKeyPair();

        byte[] warBytes = createValidWarBytes("candidate-v1.1.0");
        Path stagedWar = testBaseDir.resolve("staging/update-tx/rupeecrm-update.war");
        Files.write(stagedWar, warBytes);

        String warHash = UpdateCrypto.computeSha256(warBytes);
        UpdateManifest manifest = new UpdateManifest(
                1, "1.1.0", "v1.1.0", "now", "1.0.0",
                "rupeecrm-update.war", "https://example.com/rupeecrm.war",
                warHash, warBytes.length, "Release notes"
        );

        byte[] manifestBytes = manifest.toJson().getBytes(StandardCharsets.UTF_8);
        String untrustedSignature = UpdateCrypto.sign(manifestBytes, untrustedKey.getPrivate());

        Path txDir = testBaseDir.resolve("staging/update-tx");
        Files.write(txDir.resolve("manifest.json"), manifestBytes);
        Files.writeString(txDir.resolve("manifest.sig"), untrustedSignature);

        // Create handoff token
        Path handoff = testBaseDir.resolve("staging/update.handoff");
        Files.writeString(handoff, "transactionId=tx-1\ntargetVersion=1.1.0\n");

        boolean activated = launcher.applyPendingUpdateIfPresent();
        assertFalse(activated, "Must reject staged update signed with untrusted key");
        assertFalse(Files.exists(handoff), "Handoff token must be invalidated on rejection");
        assertEquals("live-v1.0.0-content", Files.readString(currentWar.toPath()));
    }

    @Test
    void testMismatchedSha256RejectsActivation() throws Exception {
        createLiveWar("live-v1.0.0-content");

        // Manifest has hash A, but staged file has hash B
        byte[] warBytes = createValidWarBytes("candidate-v1.1.0");
        Path stagedWar = testBaseDir.resolve("staging/update-tx/rupeecrm-update.war");
        Files.write(stagedWar, warBytes);

        UpdateManifest manifest = new UpdateManifest(
                1, "1.1.0", "v1.1.0", "now", "1.0.0",
                "rupeecrm-update.war", "https://example.com/rupeecrm.war",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", // mismatched hash
                warBytes.length, "Release notes"
        );

        Path txDir = testBaseDir.resolve("staging/update-tx");
        Files.write(txDir.resolve("manifest.json"), manifest.toJson().getBytes(StandardCharsets.UTF_8));
        Files.writeString(txDir.resolve("manifest.sig"), "dGVzdC1zaWduYXR1cmU="); // dummy

        Path handoff = testBaseDir.resolve("staging/update.handoff");
        Files.writeString(handoff, "transactionId=tx-1\ntargetVersion=1.1.0\n");

        boolean activated = launcher.applyPendingUpdateIfPresent();
        assertFalse(activated, "Must reject staged update with mismatched hash or invalid signature");
    }

    @Test
    void testNonZipArtifactRejectsActivation() throws Exception {
        createLiveWar("live-v1.0.0-content");

        // File does not have ZIP magic bytes
        byte[] nonZipBytes = new byte[20_000];
        Path stagedWar = testBaseDir.resolve("staging/update-tx/rupeecrm-update.war");
        Files.write(stagedWar, nonZipBytes);

        Path handoff = testBaseDir.resolve("staging/update.handoff");
        Files.writeString(handoff, "transactionId=tx-1\ntargetVersion=1.1.0\n");

        boolean activated = launcher.applyPendingUpdateIfPresent();
        assertFalse(activated, "Must reject non-ZIP artifact");
    }

    @Test
    void testSuccessfulActivationAndBackupCreation() throws Exception {
        File currentWar = createLiveWar("live-v1.0.0-content");

        // Generate a test Ed25519 keypair and set as trusted public key
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair testKeyPair = kpg.generateKeyPair();
        String testPubKeyBase64 = Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded());
        System.setProperty("app.update.public-key", testPubKeyBase64);

        try {
            byte[] warBytes = createValidWarBytes("candidate-v1.2.0-binary-content");
            Path stagedWar = testBaseDir.resolve("staging/update-tx/rupeecrm-update.war");
            Files.write(stagedWar, warBytes);

            String warHash = UpdateCrypto.computeSha256(warBytes);
            UpdateManifest manifest = new UpdateManifest(
                    1, "1.2.0", "v1.2.0", "2026-10-19T20:00:00Z", "1.0.0",
                    "rupeecrm-update.war", "https://example.com/rupeecrm-update.war",
                    warHash, warBytes.length, "Version 1.2.0 release notes"
            );

            byte[] manifestBytes = manifest.toJson().getBytes(StandardCharsets.UTF_8);
            String signature = UpdateCrypto.sign(manifestBytes, testKeyPair.getPrivate());

            Path txDir = testBaseDir.resolve("staging/update-tx");
            Files.write(txDir.resolve("manifest.json"), manifestBytes);
            Files.writeString(txDir.resolve("manifest.sig"), signature);

            Path handoff = testBaseDir.resolve("staging/update.handoff");
            Files.writeString(handoff, "transactionId=tx-test-success\ntargetVersion=1.2.0\n");

            // Apply update
            boolean activated = launcher.applyPendingUpdateIfPresent();
            assertTrue(activated, "Valid signed update must be successfully activated");

            // 1. Live WAR must now contain the candidate bytes
            byte[] newLiveBytes = Files.readAllBytes(currentWar.toPath());
            assertArrayEquals(warBytes, newLiveBytes);

            // 2. Backup WAR (.bak) must exist with original content
            File backupWar = new File(currentWar.getParentFile(), currentWar.getName() + ".bak");
            assertTrue(backupWar.exists(), "Rollback backup (.bak) must exist");
            assertEquals("live-v1.0.0-content", Files.readString(backupWar.toPath()));

            // 3. Handoff token must be consumed
            assertFalse(Files.exists(handoff), "Handoff token must be removed upon activation");

            // 4. Update state must be STARTING_NEW_VERSION
            Path stateFile = testBaseDir.resolve("staging/update-state.json");
            assertTrue(Files.exists(stateFile), "State file must exist");
            UpdateState state = UpdateState.load(stateFile);
            assertEquals(UpdateState.STATUS_STARTING_NEW_VERSION, state.getStatus());
            assertEquals("1.2.0", state.getTargetVersion());
            assertEquals(backupWar.getAbsolutePath(), state.getBackupArtifactPath());
        } finally {
            System.clearProperty("app.update.public-key");
        }
    }

    @Test
    void testRecoverInterruptedTransactionResumesCandidateTracking() throws Exception {
        File currentWar = createLiveWar("candidate-v1.3.0-content");
        File backupWar = new File(currentWar.getParentFile(), currentWar.getName() + ".bak");
        Files.writeString(backupWar.toPath(), "live-v1.2.0-backup-content");

        Path stateFile = testBaseDir.resolve("staging/update-state.json");
        UpdateState state = new UpdateState();
        state.setTargetVersion("1.3.0");
        state.setStatus(UpdateState.STATUS_STARTING_NEW_VERSION);
        state.setBackupArtifactPath(backupWar.getAbsolutePath());
        state.saveAtomic(stateFile);

        LauncherMain newSupervisor = new LauncherMain();
        assertFalse(newSupervisor.isCandidateLaunch());

        newSupervisor.recoverInterruptedTransactionIfPresent();

        assertTrue(newSupervisor.isCandidateLaunch(), "Supervisor must resume candidate tracking for STARTING_NEW_VERSION");
        assertEquals("1.3.0", newSupervisor.getCandidateVersion());
        assertNotNull(newSupervisor.getActiveBackupWar());
        assertEquals(backupWar.getAbsolutePath(), newSupervisor.getActiveBackupWar().getAbsolutePath());
    }

    @Test
    void testRecoverInterruptedTransactionRestoresBinaryWhenInterruptedDuringSwap() throws Exception {
        File currentWar = createLiveWar("partially-written-broken-binary");
        File backupWar = new File(currentWar.getParentFile(), currentWar.getName() + ".bak");
        Files.writeString(backupWar.toPath(), "healthy-original-binary");

        Path stateFile = testBaseDir.resolve("staging/update-state.json");
        UpdateState state = new UpdateState();
        state.setTargetVersion("1.3.0");
        state.setStatus(UpdateState.STATUS_ACTIVATION_STARTED);
        state.setBackupArtifactPath(backupWar.getAbsolutePath());
        state.saveAtomic(stateFile);

        LauncherMain newSupervisor = new LauncherMain();
        newSupervisor.recoverInterruptedTransactionIfPresent();

        assertFalse(newSupervisor.isCandidateLaunch(), "Should not launch broken candidate");
        assertEquals("healthy-original-binary", Files.readString(currentWar.toPath()), "Must restore healthy backup binary");

        UpdateState recoveredState = UpdateState.load(stateFile);
        assertEquals(UpdateState.STATUS_ROLLED_BACK, recoveredState.getStatus());
    }

    @Test
    void testRecoverInterruptedTransactionFailsClosedWhenBackupMissing() throws Exception {
        createLiveWar("partially-written-broken-binary");

        Path stateFile = testBaseDir.resolve("staging/update-state.json");
        UpdateState state = new UpdateState();
        state.setTargetVersion("1.3.0");
        state.setStatus(UpdateState.STATUS_ACTIVATION_STARTED);
        state.setBackupArtifactPath(testBaseDir.resolve("app/non-existent.bak").toString());
        state.saveAtomic(stateFile);

        LauncherMain newSupervisor = new LauncherMain();
        newSupervisor.recoverInterruptedTransactionIfPresent();

        UpdateState recoveredState = UpdateState.load(stateFile);
        assertEquals(UpdateState.STATUS_RECOVERY_REQUIRED, recoveredState.getStatus(),
                "Must transition to RECOVERY_REQUIRED when backup file is missing");
        assertTrue(recoveredState.getErrorMessage().contains("missing or corrupted"));
    }

    @Test
    void testRecoverInterruptedTransactionFailsClosedWhenBackupZeroLength() throws Exception {
        createLiveWar("partially-written-broken-binary");
        File emptyBackupWar = testBaseDir.resolve("app/empty.bak").toFile();
        Files.write(emptyBackupWar.toPath(), new byte[0]); // 0 bytes

        Path stateFile = testBaseDir.resolve("staging/update-state.json");
        UpdateState state = new UpdateState();
        state.setTargetVersion("1.3.0");
        state.setStatus(UpdateState.STATUS_ACTIVATION_STARTED);
        state.setBackupArtifactPath(emptyBackupWar.getAbsolutePath());
        state.saveAtomic(stateFile);

        LauncherMain newSupervisor = new LauncherMain();
        newSupervisor.recoverInterruptedTransactionIfPresent();

        UpdateState recoveredState = UpdateState.load(stateFile);
        assertEquals(UpdateState.STATUS_RECOVERY_REQUIRED, recoveredState.getStatus(),
                "Must transition to RECOVERY_REQUIRED when backup file is 0 bytes");
    }

    @Test
    void testRecoverInterruptedTransactionIsIdempotent() throws Exception {
        File currentWar = createLiveWar("partially-written-broken-binary");
        File backupWar = new File(currentWar.getParentFile(), currentWar.getName() + ".bak");
        Files.writeString(backupWar.toPath(), "healthy-original-binary");

        Path stateFile = testBaseDir.resolve("staging/update-state.json");
        UpdateState state = new UpdateState();
        state.setTargetVersion("1.3.0");
        state.setStatus(UpdateState.STATUS_ACTIVATION_STARTED);
        state.setBackupArtifactPath(backupWar.getAbsolutePath());
        state.saveAtomic(stateFile);

        // Run recovery 3 times consecutively
        LauncherMain supervisor1 = new LauncherMain();
        supervisor1.recoverInterruptedTransactionIfPresent();

        LauncherMain supervisor2 = new LauncherMain();
        supervisor2.recoverInterruptedTransactionIfPresent();

        LauncherMain supervisor3 = new LauncherMain();
        supervisor3.recoverInterruptedTransactionIfPresent();

        assertEquals("healthy-original-binary", Files.readString(currentWar.toPath()));
        UpdateState finalState = UpdateState.load(stateFile);
        assertEquals(UpdateState.STATUS_ROLLED_BACK, finalState.getStatus());
    }

    @Test
    void testRecoverInterruptedTransactionHandlesMalformedStateFileSafely() throws Exception {
        createLiveWar("live-v1.0.0-content");
        Path stateFile = testBaseDir.resolve("staging/update-state.json");
        Files.writeString(stateFile, "{ broken malformed JSON !!! }");

        LauncherMain supervisor = new LauncherMain();
        assertDoesNotThrow(() -> supervisor.recoverInterruptedTransactionIfPresent(),
                "Must not throw unhandled exception on corrupted state file");
    }
}
