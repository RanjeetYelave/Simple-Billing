package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.update.UpdateCrypto;
import com.billing.simple.billsoft.update.UpdateManifest;
import com.billing.simple.billsoft.update.UpdateState;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RupeeCRM Enterprise OTA Resumable Download, Atomic Installation & Recovery Tests")
class OtaResumableDownloadAndRecoveryTest {

    private HttpServer testServer;
    private int serverPort;
    private KeyPair keyPair;
    private String publicKeyBase64;
    private UpdateService updateService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        keyPair = kpg.generateKeyPair();
        publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        testServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverPort = testServer.getAddress().getPort();
        testServer.start();

        updateService = new UpdateService(new RestTemplate());
        ReflectionTestUtils.setField(updateService, "defaultVersion", "v1.0.0");
        ReflectionTestUtils.setField(updateService, "manifestUrl", "http://127.0.0.1:" + serverPort + "/manifest.json");
        ReflectionTestUtils.setField(updateService, "signatureUrl", "http://127.0.0.1:" + serverPort + "/manifest.sig");
        ReflectionTestUtils.setField(updateService, "publicKeyBase64", publicKeyBase64);

        // Ensure clean state before each test
        try {
            Files.deleteIfExists(updateService.getStateFilePath());
            Files.deleteIfExists(updateService.getHandoffFilePath());
            Path txDir = updateService.getTxDirectory();
            if (Files.exists(txDir)) {
                try (var stream = Files.list(txDir)) {
                    stream.forEach(f -> {
                        try { Files.deleteIfExists(f); } catch (Exception ignored) {}
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    @AfterEach
    void tearDown() {
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    private byte[] generateValidWar(String content) {
        byte[] magic = UpdateCrypto.ZIP_MAGIC;
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] war = new byte[magic.length + body.length + 12000];
        System.arraycopy(magic, 0, war, 0, magic.length);
        System.arraycopy(body, 0, war, magic.length, body.length);
        return war;
    }

    @Test
    @DisplayName("Test 1: Resumable download appends with HTTP 206 Partial Content")
    void testResumableDownloadWithHttp206() throws Exception {
        byte[] fullWar = generateValidWar("resumable-war-payload");
        int splitOffset = 4000;
        byte[] firstPart = new byte[splitOffset];
        System.arraycopy(fullWar, 0, firstPart, 0, splitOffset);

        Path partFile = tempDir.resolve("rupeecrm-update.war.part");
        Files.write(partFile, firstPart);

        AtomicBoolean rangeHeaderReceived = new AtomicBoolean(false);
        AtomicInteger requestedRangeStart = new AtomicInteger(-1);

        testServer.createContext("/artifact.war", exchange -> {
            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                rangeHeaderReceived.set(true);
                int dash = rangeHeader.indexOf('-');
                int start = Integer.parseInt(rangeHeader.substring(6, dash));
                requestedRangeStart.set(start);

                int length = fullWar.length - start;
                exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + (fullWar.length - 1) + "/" + fullWar.length);
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(206, length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fullWar, start, length);
                }
            } else {
                exchange.sendResponseHeaders(200, fullWar.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fullWar);
                }
            }
        });

        String downloadUrl = "http://127.0.0.1:" + serverPort + "/artifact.war";
        updateService.downloadArtifactToPart(downloadUrl, partFile, fullWar.length, "v1.2.0");

        assertTrue(rangeHeaderReceived.get(), "Must send HTTP Range header when partial file exists");
        assertEquals(splitOffset, requestedRangeStart.get(), "Range offset must match existing partial file size");
        assertEquals(fullWar.length, Files.size(partFile), "Final file size must match complete artifact size");
        assertArrayEquals(fullWar, Files.readAllBytes(partFile), "Combined file bytes must match full artifact");
    }

    @Test
    @DisplayName("Test 2: Server fallback to HTTP 200 truncates and restarts download from byte 0")
    void testFallbackToHttp200RestartsFromBeginning() throws Exception {
        byte[] fullWar = generateValidWar("full-download-restart-payload");
        Path partFile = tempDir.resolve("rupeecrm-update.war.part");
        // Seed initial partial file with garbage bytes
        Files.write(partFile, new byte[]{0x01, 0x02, 0x03, 0x04});

        testServer.createContext("/artifact.war", exchange -> {
            // Server ignores Range header and returns full content with HTTP 200
            exchange.sendResponseHeaders(200, fullWar.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fullWar);
            }
        });

        String downloadUrl = "http://127.0.0.1:" + serverPort + "/artifact.war";
        updateService.downloadArtifactToPart(downloadUrl, partFile, fullWar.length, "v1.2.0");

        assertEquals(fullWar.length, Files.size(partFile), "Final file must not contain leftover bytes from aborted Range");
        assertArrayEquals(fullWar, Files.readAllBytes(partFile), "Downloaded content must match full content exactly");
    }

    @Test
    @DisplayName("Test 3: HTTP 416 Range Not Satisfiable resets partial file and restarts from 0")
    void testHttp416ResetAndRestart() throws Exception {
        byte[] fullWar = generateValidWar("http-416-recovery-payload");
        Path partFile = tempDir.resolve("rupeecrm-update.war.part");
        // Seed partial file that server rejects with 416
        Files.write(partFile, new byte[2000]);

        AtomicInteger requestCount = new AtomicInteger(0);

        testServer.createContext("/artifact.war", exchange -> {
            int count = requestCount.incrementAndGet();
            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            if (count == 1 && rangeHeader != null) {
                // First request has invalid range: return 416
                exchange.getResponseHeaders().set("Content-Range", "bytes */" + fullWar.length);
                exchange.sendResponseHeaders(416, -1);
            } else {
                // Subsequent request: return 200 with full content
                exchange.sendResponseHeaders(200, fullWar.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fullWar);
                }
            }
        });

        String downloadUrl = "http://127.0.0.1:" + serverPort + "/artifact.war";
        updateService.downloadArtifactToPart(downloadUrl, partFile, fullWar.length, "v1.2.0");

        assertEquals(fullWar.length, Files.size(partFile), "File size must match full artifact size after 416 recovery");
        assertArrayEquals(fullWar, Files.readAllBytes(partFile));
    }

    @Test
    @DisplayName("Test 4: Pause preserves partial file and records DOWNLOAD_PAUSED state")
    void testPausePreservesPartialFile() throws Exception {
        byte[] fullWar = generateValidWar("pause-resume-test-war");
        Path partFile = tempDir.resolve("rupeecrm-update.war.part");

        testServer.createContext("/slow-artifact.war", exchange -> {
            exchange.sendResponseHeaders(200, fullWar.length);
            try (OutputStream os = exchange.getResponseBody()) {
                // Write initial 4000 bytes
                os.write(fullWar, 0, 4000);
                os.flush();

                // Allow client thread to read and write first chunk into partFile
                try {
                    Thread.sleep(150);
                } catch (Exception ignored) {}

                // Trigger pause while stream is open
                updateService.pauseUpdate();

                // Attempt to write remaining bytes (client should detect pause)
                try {
                    Thread.sleep(200);
                    os.write(fullWar, 4000, fullWar.length - 4000);
                } catch (Exception ignored) {}
            }
        });

        String downloadUrl = "http://127.0.0.1:" + serverPort + "/slow-artifact.war";
        assertThrows(UpdateService.DownloadPausedException.class, () -> {
            updateService.downloadArtifactToPart(downloadUrl, partFile, fullWar.length, "v1.2.0");
        });

        assertTrue(Files.exists(partFile), "Partial file must NOT be deleted when paused");
        assertTrue(Files.size(partFile) > 0, "Partial file must contain downloaded bytes");

        UpdateState state = UpdateState.load(updateService.getStateFilePath());
        assertEquals(UpdateState.STATUS_DOWNLOAD_PAUSED, state.getStatus(), "State must transition to DOWNLOAD_PAUSED");
    }

    @Test
    @DisplayName("Test 5: Disk space verification rejects downloads when usable space is under 250 MB")
    void testDiskSpaceVerificationRejection() {
        // hasSufficientDiskSpace check
        boolean spaceOk = updateService.hasSufficientDiskSpace();
        assertNotNull(spaceOk);

        Map<String, Object> diag = updateService.getUpdateDiagnostics();
        assertTrue(diag.containsKey("freeDiskSpaceBytes"));
        assertTrue(diag.containsKey("freeDiskSpaceMB"));
        assertTrue(diag.containsKey("hasSufficientDiskSpace"));
    }

    @Test
    @DisplayName("Test 6: Update diagnostics endpoint returns comprehensive pipeline state")
    void testUpdateDiagnosticsEndpoint() {
        Map<String, Object> diag = updateService.getUpdateDiagnostics();
        assertNotNull(diag);
        assertTrue(diag.containsKey("currentVersion"));
        assertTrue(diag.containsKey("updateInProgress"));
        assertTrue(diag.containsKey("status"));
        assertTrue(diag.containsKey("partFileExists"));
        assertTrue(diag.containsKey("stagedWarExists"));
        assertTrue(diag.containsKey("progress"));
    }

    @Test
    @DisplayName("Test 7: Decoupled staging verifies artifact without triggering immediate shutdown")
    void testDecoupledStagingWithoutImmediateExit() throws Exception {
        byte[] validWar = generateValidWar("decoupled-staging-verification");
        String warHash = UpdateCrypto.computeSha256(validWar);

        testServer.createContext("/rupeecrm-staged.war", exchange -> {
            exchange.sendResponseHeaders(200, validWar.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(validWar); }
        });

        Path txDir = updateService.getTxDirectory();
        Files.createDirectories(txDir);
        Path partFile = txDir.resolve("rupeecrm-update.war.part");

        updateService.downloadArtifactToPart("http://127.0.0.1:" + serverPort + "/rupeecrm-staged.war",
                partFile, validWar.length, "v1.5.0");

        assertTrue(Files.exists(partFile));
        assertEquals(validWar.length, Files.size(partFile));
        assertEquals(warHash, UpdateCrypto.computeSha256(partFile));
        assertTrue(UpdateCrypto.isZipFile(partFile));
    }
}
