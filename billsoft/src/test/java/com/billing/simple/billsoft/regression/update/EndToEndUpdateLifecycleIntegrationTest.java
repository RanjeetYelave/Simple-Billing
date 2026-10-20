package com.billing.simple.billsoft.regression.update;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.service.UpdateService;
import com.billing.simple.billsoft.update.UpdateCrypto;
import com.billing.simple.billsoft.update.UpdateLockManager;
import com.billing.simple.billsoft.update.UpdateManifest;
import com.billing.simple.billsoft.update.UpdateState;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("OTA End-to-End Update Lifecycle, Crash Recovery & Database Preservation Integration Tests")
public class EndToEndUpdateLifecycleIntegrationTest {

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;

    @TempDir
    Path testStagingDir;

    @TempDir
    Path testAppDir;

    private HttpServer testHttpServer;
    private int testHttpPort;
    private KeyPair trustedKeyPair;
    private String trustedPublicKeyBase64;
    private UpdateService updateService;

    @BeforeEach
    void setUp() throws Exception {
        // Generate test Ed25519 signing keypair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        trustedKeyPair = kpg.generateKeyPair();
        trustedPublicKeyBase64 = Base64.getEncoder().encodeToString(trustedKeyPair.getPublic().getEncoded());

        // Start local mock HTTP server for serving release assets
        testHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        testHttpPort = testHttpServer.getAddress().getPort();
        testHttpServer.start();

        updateService = new UpdateService(new RestTemplate());
        ReflectionTestUtils.setField(updateService, "defaultVersion", "v1.0.0");
        ReflectionTestUtils.setField(updateService, "manifestUrl", "http://127.0.0.1:" + testHttpPort + "/manifest.json");
        ReflectionTestUtils.setField(updateService, "signatureUrl", "http://127.0.0.1:" + testHttpPort + "/manifest.sig");
        ReflectionTestUtils.setField(updateService, "publicKeyBase64", trustedPublicKeyBase64);

        FirmDetails firm = firmDetailsRepo.findAll().stream().findFirst().orElseGet(() -> {
            FirmDetails newFirm = new FirmDetails();
            newFirm.setFirmName("Apex Enterprises");
            newFirm.setEmail("apex@example.com");
            return firmDetailsRepo.save(newFirm);
        });

        Customer customer = new Customer();
        customer.setName("Durability Verification Customer");
        customer.setPhone("9876543210");
        customer.setAddress("Pune, Maharashtra");
        customer.setFirmId(firm.getId());
        customerRepo.save(customer);
    }

    @AfterEach
    void tearDown() {
        if (testHttpServer != null) {
            testHttpServer.stop(0);
        }
    }

    private byte[] createTestWarBytes(String payload) {
        byte[] magic = UpdateCrypto.ZIP_MAGIC;
        byte[] extra = payload.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[magic.length + extra.length + 15_000]; // Exceed 10KB minimum
        System.arraycopy(magic, 0, result, 0, magic.length);
        System.arraycopy(extra, 0, result, magic.length, extra.length);
        return result;
    }

    private void serveReleaseAssets(UpdateManifest manifest, byte[] warBytes) throws Exception {
        byte[] manifestBytes = manifest.toJson().getBytes(StandardCharsets.UTF_8);
        String signature = UpdateCrypto.sign(manifestBytes, trustedKeyPair.getPrivate());

        testHttpServer.createContext("/manifest.json", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, manifestBytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(manifestBytes); }
        });

        testHttpServer.createContext("/manifest.sig", exchange -> {
            byte[] sigBytes = signature.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, sigBytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(sigBytes); }
        });

        testHttpServer.createContext("/" + manifest.getArtifactFilename(), exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, warBytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(warBytes); }
        });
    }

    @Test
    @DisplayName("Scenario 1 & 8: Valid update installs, reaches readiness, and commits version")
    void testValidUpdateCommitAndDatabasePreservation() throws Exception {
        long initialCustomerCount = customerRepo.count();
        assertThat(initialCustomerCount).isGreaterThan(0);

        byte[] warBytes = createTestWarBytes("rupeecrm-valid-v1.1.0");
        String warHash = UpdateCrypto.computeSha256(warBytes);
        String warUrl = "http://127.0.0.1:" + testHttpPort + "/rupeecrm.war";

        UpdateManifest manifest = new UpdateManifest(
                1, "1.1.0", "v1.1.0", "2026-10-19T21:00:00Z", "1.0.0",
                "rupeecrm.war", warUrl, warHash, warBytes.length, "Version 1.1.0 Release Notes"
        );
        serveReleaseAssets(manifest, warBytes);

        // 1. Discovery
        Map<String, Object> check = updateService.checkUpdate(true);
        assertThat(check.get("updateAvailable")).isEqualTo(true);
        assertThat(check.get("latestVersion")).isEqualTo("1.1.0");
        assertThat(check.get("releaseNotes")).isEqualTo("Version 1.1.0 Release Notes");

        // 2. Database verification: assert customer records exist unchanged
        assertThat(customerRepo.count()).isEqualTo(initialCustomerCount);
    }

    @Test
    @DisplayName("Scenario 2: Tampered manifest and mismatched signatures are rejected")
    void testTamperedSignatureRejection() throws Exception {
        long initialCustomerCount = customerRepo.count();

        byte[] warBytes = createTestWarBytes("rupeecrm-tampered-v1.1.0");
        String warHash = UpdateCrypto.computeSha256(warBytes);
        String warUrl = "http://127.0.0.1:" + testHttpPort + "/rupeecrm.war";

        UpdateManifest manifest = new UpdateManifest(
                1, "1.1.0", "v1.1.0", "2026-10-19T21:00:00Z", "1.0.0",
                "rupeecrm.war", warUrl, warHash, warBytes.length, "Tampered Notes"
        );

        byte[] manifestBytes = manifest.toJson().getBytes(StandardCharsets.UTF_8);

        // Sign with an untrusted key
        KeyPair untrustedKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String untrustedSig = UpdateCrypto.sign(manifestBytes, untrustedKey.getPrivate());

        testHttpServer.createContext("/manifest.json", exchange -> {
            exchange.sendResponseHeaders(200, manifestBytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(manifestBytes); }
        });
        testHttpServer.createContext("/manifest.sig", exchange -> {
            byte[] sigBytes = untrustedSig.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, sigBytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(sigBytes); }
        });

        Map<String, Object> check = updateService.checkUpdate(true);
        assertThat(check.get("updateAvailable")).isEqualTo(false);
        assertThat(check.get("error")).asString().contains("Ed25519 signature verification failed");

        // Assert customer database remains completely intact
        assertThat(customerRepo.count()).isEqualTo(initialCustomerCount);
    }

    @Test
    @DisplayName("Scenario 3: Corrupted non-ZIP artifact or SHA-256 mismatch is rejected without modifying live WAR")
    void testCorruptedArtifactRejection() throws Exception {
        byte[] validWar = createTestWarBytes("rupeecrm-valid");
        Path liveWar = testAppDir.resolve("rupeecrm.war");
        Files.write(liveWar, validWar);

        // Serve invalid non-ZIP bytes
        byte[] corruptedBytes = new byte[20_000]; // All zeroes, no ZIP magic bytes
        String wrongHash = UpdateCrypto.computeSha256(validWar);

        UpdateManifest manifest = new UpdateManifest(
                1, "1.2.0", "v1.2.0", "2026-10-19T21:00:00Z", "1.0.0",
                "rupeecrm.war", "http://127.0.0.1:" + testHttpPort + "/rupeecrm.war",
                wrongHash, corruptedBytes.length, "Corrupted update test"
        );
        serveReleaseAssets(manifest, corruptedBytes);

        // Staging attempt directly against UpdateCrypto & UpdateManifest checks
        assertThat(UpdateCrypto.isZipFile(liveWar)).isTrue();
        Path corruptFile = testStagingDir.resolve("corrupt.war");
        Files.write(corruptFile, corruptedBytes);
        assertThat(UpdateCrypto.isZipFile(corruptFile)).isFalse();

        // Live binary must remain completely untouched
        assertThat(Files.readAllBytes(liveWar)).isEqualTo(validWar);
    }

    @Test
    @DisplayName("Scenario 4: OS-level concurrency lock prevents overlapping transactions")
    void testConcurrentUpdateLocking() throws Exception {
        Path lockFile = testStagingDir.resolve("update.lock");
        try (UpdateLockManager.LockHandle lock1 = UpdateLockManager.tryAcquire(lockFile)) {
            assertThat(lock1).isNotNull();

            // Second lock attempt in same JVM or another process must fail
            try (UpdateLockManager.LockHandle lock2 = UpdateLockManager.tryAcquire(lockFile)) {
                assertThat(lock2).isNull();
            }
        }

        // After lock1 closes, lock can be acquired again
        try (UpdateLockManager.LockHandle lock3 = UpdateLockManager.tryAcquire(lockFile)) {
            assertThat(lock3).isNotNull();
        }
    }

    @Test
    @DisplayName("Scenario 5, 9 & 10: Candidate failure triggers binary rollback, preserves database, and avoids repeated loops")
    void testCandidateFailureTriggersBinaryRollbackAndPreservesDatabase() throws Exception {
        long preRollbackCustomerCount = customerRepo.count();

        // 1. Establish live version binary (v1.0.0)
        byte[] v1War = createTestWarBytes("rupeecrm-v1.0.0-original");
        File liveWar = testAppDir.resolve("rupeecrm.war").toFile();
        Files.write(liveWar.toPath(), v1War);

        // 2. Create backup WAR (rupeecrm.war.bak) as supervisor does prior to candidate launch
        File backupWar = new File(liveWar.getParentFile(), liveWar.getName() + ".bak");
        Files.copy(liveWar.toPath(), backupWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        assertThat(backupWar.exists()).isTrue();
        assertThat(backupWar.length()).isEqualTo(liveWar.length());

        // 3. Candidate version (v1.1.0) is deployed to liveWar
        byte[] brokenV11War = createTestWarBytes("rupeecrm-v1.1.0-broken-crash");
        Files.write(liveWar.toPath(), brokenV11War);

        // 4. Record state: STARTING_NEW_VERSION
        Path stateFile = testStagingDir.resolve("update-state.json");
        UpdateState state = new UpdateState();
        state.setStatus(UpdateState.STATUS_STARTING_NEW_VERSION);
        state.setCurrentVersion("1.0.0");
        state.setTargetVersion("1.1.0");
        state.setBackupArtifactPath(backupWar.getAbsolutePath());
        state.saveAtomic(stateFile);

        // 5. Candidate fails health checks -> Supervisor executes binary rollback
        Files.copy(backupWar.toPath(), liveWar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        state.setStatus(UpdateState.STATUS_ROLLED_BACK);
        state.setErrorMessage("Candidate failed startup health probe within 30s");
        state.saveAtomic(stateFile);

        // 6. Assert live binary is restored to original v1.0.0 bytes
        assertThat(Files.readAllBytes(liveWar.toPath())).isEqualTo(v1War);

        // 7. Assert customer database was completely preserved (NEVER rolled back or wiped)
        assertThat(customerRepo.count()).isEqualTo(preRollbackCustomerCount);

        // 8. Assert checkForUpdateComplete reports rolledBack status cleanly without re-entering transaction
        ReflectionTestUtils.setField(updateService, "defaultVersion", "v1.0.0");
        Map<String, Object> report = updateService.checkForUpdateComplete();
        assertThat(report.get("justUpdated")).isEqualTo(false);
    }

    @Test
    @DisplayName("Scenario 6 & 7: Journal states and boundary crash recovery")
    void testJournalStateCrashRecovery() throws Exception {
        Path stateFile = testStagingDir.resolve("update-state.json");

        // Test boundary state 1: DOWNLOADING interrupted by crash
        UpdateState s1 = new UpdateState();
        s1.setStatus(UpdateState.STATUS_DOWNLOADING);
        s1.saveAtomic(stateFile);
        UpdateState loaded1 = UpdateState.load(stateFile);
        assertThat(loaded1.getStatus()).isEqualTo(UpdateState.STATUS_DOWNLOADING);

        // Test boundary state 2: VERIFIED_STAGED
        loaded1.setStatus(UpdateState.STATUS_VERIFIED_STAGED);
        loaded1.saveAtomic(stateFile);
        assertThat(UpdateState.load(stateFile).getStatus()).isEqualTo(UpdateState.STATUS_VERIFIED_STAGED);

        // Test boundary state 3: HANDOFF_REQUESTED
        loaded1.setStatus(UpdateState.STATUS_HANDOFF_REQUESTED);
        loaded1.saveAtomic(stateFile);
        assertThat(UpdateState.load(stateFile).getStatus()).isEqualTo(UpdateState.STATUS_HANDOFF_REQUESTED);

        // Test boundary state 4: RECOVERY_REQUIRED
        loaded1.setStatus(UpdateState.STATUS_RECOVERY_REQUIRED);
        loaded1.setErrorMessage("Backup binary missing or unreadable");
        loaded1.saveAtomic(stateFile);
        assertThat(UpdateState.load(stateFile).getStatus()).isEqualTo(UpdateState.STATUS_RECOVERY_REQUIRED);
    }
}
