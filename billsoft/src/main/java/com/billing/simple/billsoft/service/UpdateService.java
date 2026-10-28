package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.update.UpdateCrypto;
import com.billing.simple.billsoft.update.UpdateLockManager;
import com.billing.simple.billsoft.update.UpdateManifest;
import com.billing.simple.billsoft.update.UpdateState;
import com.billing.simple.billsoft.util.DataDirectoryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.sql.DataSource;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Enterprise-grade OTA update engine for RupeeCRM.
 * Enforces cryptographic Ed25519 manifest verification, atomic .part downloads,
 * OS-level transaction locking, online H2 database quarantine snapshots, and safe handoff.
 */
@Service
public class UpdateService {

    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);

    public static final String DEFAULT_PUBLIC_KEY =
            "MCowBQYDK2VwAyEAtdG8eJgrX0L7jtx13wOGnqSeeID2B/aRv1+OZQ7v3C8=";

    @Value("${app.version:v1.0.0}")
    private String defaultVersion = "v1.0.0";

    @Value("${app.update.check.enabled:true}")
    private boolean updateCheckEnabled = true;

    @Value("${app.update.manifest-url:https://github.com/RanjeetYelave/Simple-Billing/releases/latest/download/manifest.json}")
    private String manifestUrl;

    @Value("${app.update.signature-url:https://github.com/RanjeetYelave/Simple-Billing/releases/latest/download/manifest.sig}")
    private String signatureUrl;

    @Value("${app.update.public-key:" + DEFAULT_PUBLIC_KEY + "}")
    private String publicKeyBase64;

    private final RestTemplate restTemplate;
    private final DataSource dataSource;

    // Progress tracking
    private final AtomicReference<String> progressStatus = new AtomicReference<>("idle");
    private final AtomicInteger progressPercent = new AtomicInteger(0);
    private final AtomicReference<String> progressMessage = new AtomicReference<>("");
    private final AtomicReference<String> progressLatestVersion = new AtomicReference<>("");
    private final AtomicReference<String> progressSpeed = new AtomicReference<>("");
    private final AtomicReference<String> progressSize = new AtomicReference<>("");
    private final AtomicBoolean updateInProgress = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private volatile HttpURLConnection activeDownloadConnection = null;

    private volatile SseEmitter progressEmitter;

    // In-memory cache for update check (5 minutes max)
    private long lastCheckTime = 0;
    private Map<String, Object> cachedResponse = null;
    private static final long CACHE_DURATION_MS = 5L * 60 * 1000; // 5 minutes

    // Cached verified manifest from latest check
    private final AtomicReference<UpdateManifest> latestVerifiedManifest = new AtomicReference<>(null);
    private final AtomicReference<byte[]> latestVerifiedManifestBytes = new AtomicReference<>(null);
    private final AtomicReference<String> latestVerifiedSignature = new AtomicReference<>(null);

    @Autowired
    public UpdateService(RestTemplate restTemplate, @Autowired(required = false) DataSource dataSource) {
        if (restTemplate != null) {
            this.restTemplate = restTemplate;
        } else {
            org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(4000);
            factory.setReadTimeout(6000);
            this.restTemplate = new RestTemplate(factory);
        }
        this.dataSource = dataSource;
    }

    public UpdateService(RestTemplate restTemplate) {
        this(restTemplate, null);
    }

    public UpdateService() {
        this(null, null);
    }

    public Path getStagingDirectory() {
        return DataDirectoryResolver.resolveStagingDirectory().toPath();
    }

    public Path getTxDirectory() {
        return getStagingDirectory().resolve("update-tx");
    }

    public Path getLockFilePath() {
        return getStagingDirectory().resolve("update.lock");
    }

    public Path getStateFilePath() {
        return getStagingDirectory().resolve("update-state.json");
    }

    public Path getHandoffFilePath() {
        return getStagingDirectory().resolve("update.handoff");
    }

    public String getCurrentVersion() {
        // 1. Check for .billsoft-version metadata override file (used by CI validation & negative lifecycle tests)
        try {
            List<Path> candidateVersionFiles = new ArrayList<>();
            java.io.File dataDirFile = DataDirectoryResolver.resolveDataDirectory();
            if (dataDirFile != null) {
                Path dataDir = dataDirFile.toPath();
                candidateVersionFiles.add(dataDir.resolve(".billsoft-version"));
                if (dataDir.getParent() != null) {
                    candidateVersionFiles.add(dataDir.getParent().resolve(".billsoft-version"));
                }
            }
            try {
                Path staging = getStagingDirectory();
                if (staging != null) {
                    candidateVersionFiles.add(staging.resolve(".billsoft-version"));
                }
            } catch (Exception ignored) {}
            candidateVersionFiles.add(Path.of(".billsoft-version"));
            candidateVersionFiles.add(Path.of("app", ".billsoft-version"));
            candidateVersionFiles.add(Path.of("app", "app", ".billsoft-version"));
            candidateVersionFiles.add(Path.of("..", ".billsoft-version"));
            candidateVersionFiles.add(Path.of("..", "app", ".billsoft-version"));
            candidateVersionFiles.add(Path.of("..", "app", "app", ".billsoft-version"));

            try {
                if (UpdateService.class.getProtectionDomain() != null &&
                    UpdateService.class.getProtectionDomain().getCodeSource() != null &&
                    UpdateService.class.getProtectionDomain().getCodeSource().getLocation() != null) {
                    Path codePath = Path.of(UpdateService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                    Path codeDir = Files.isDirectory(codePath) ? codePath : codePath.getParent();
                    if (codeDir != null) {
                        candidateVersionFiles.add(codeDir.resolve(".billsoft-version"));
                        if (codeDir.getParent() != null) {
                            candidateVersionFiles.add(codeDir.getParent().resolve(".billsoft-version"));
                        }
                    }
                }
            } catch (Exception ignored) {}

            for (Path verFile : candidateVersionFiles) {
                if (verFile != null && Files.exists(verFile)) {
                    String ver = Files.readString(verFile, StandardCharsets.UTF_8).trim();
                    if (!ver.isEmpty()) {
                        return ver;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Read from durable update state file
        Path stateFile = getStateFilePath();
        if (Files.exists(stateFile)) {
            UpdateState state = UpdateState.load(stateFile);
            if (state != null) {
                String st = state.getStatus();
                if (UpdateState.STATUS_COMMITTED.equals(st) ||
                    UpdateState.STATUS_STARTING_NEW_VERSION.equals(st) ||
                    UpdateState.STATUS_HEALTH_VERIFIED.equals(st) ||
                    UpdateState.STATUS_SUCCESS.equals(st)) {
                    if (state.getTargetVersion() != null && !state.getTargetVersion().trim().isEmpty()) {
                        return state.getTargetVersion().trim();
                    }
                }
                if (state.getCurrentVersion() != null && !state.getCurrentVersion().trim().isEmpty()) {
                    return state.getCurrentVersion().trim();
                }
            }
        }

        // 3. Check Package implementation version from running JAR/WAR manifest
        Package pkg = UpdateService.class.getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null && !pkg.getImplementationVersion().isBlank()) {
            return pkg.getImplementationVersion().trim();
        }

        return defaultVersion;
    }

    public synchronized Map<String, Object> checkUpdate() {
        return checkUpdate(false);
    }

    public synchronized Map<String, Object> checkUpdate(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && cachedResponse != null && (now - lastCheckTime) < CACHE_DURATION_MS) {
            return cachedResponse;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("automaticInstallSupported", true);
        String currentVer = getCurrentVersion();
        response.put("currentVersion", currentVer);

        if (!updateCheckEnabled || "true".equalsIgnoreCase(System.getenv("DISABLE_UPDATE_CHECK")) || "false".equalsIgnoreCase(System.getProperty("app.update.check.enabled"))) {
            response.put("updateAvailable", false);
            response.put("message", "Update check is disabled by configuration");
            return response;
        }

        try {
            // 1. Fetch manifest.json via restTemplate
            byte[] manifestBytes = restTemplate.getForObject(manifestUrl, byte[].class);
            if (manifestBytes == null || manifestBytes.length == 0) {
                throw new IllegalStateException("Empty manifest received from " + manifestUrl);
            }

            // 2. Fetch manifest.sig via restTemplate
            String sigBase64 = restTemplate.getForObject(signatureUrl, String.class);
            if (sigBase64 == null || sigBase64.trim().isEmpty()) {
                throw new IllegalStateException("Empty signature received from " + signatureUrl);
            }

            // 3. Cryptographically verify manifest before parsing or trusting
            PublicKey trustedKey = UpdateCrypto.decodePublicKey(publicKeyBase64);
            UpdateManifest manifest = UpdateManifest.parseAndVerify(manifestBytes, sigBase64.trim(), trustedKey);
            latestVerifiedManifest.set(manifest);
            latestVerifiedManifestBytes.set(manifestBytes);
            latestVerifiedSignature.set(sigBase64.trim());

            String latestVer = manifest.getAppVersion();
            response.put("latestVersion", latestVer);
            response.put("releaseTag", manifest.getReleaseTag());
            response.put("releaseNotes", manifest.getReleaseNotes());
            response.put("publishedAt", manifest.getPublishedAt());

            int cmp = compareVersions(latestVer, currentVer);
            boolean available = cmp > 0;
            response.put("updateAvailable", available);
            response.put("downloadUrl", "internal");

            lastCheckTime = now;
            cachedResponse = new HashMap<>(response);
            return response;
        } catch (Exception e) {
            log.warn("Update check failed: {}", e.getMessage());
            response.put("updateAvailable", false);
            response.put("error", "Update check failed: " + e.getMessage());
            return response;
        }
    }

    /**
     * Initiates the update download and verification asynchronously.
     * Returns true if started, false if an update is already running or no update was discovered.
     */
    public boolean startUpdateAsync() {
        cancelRequested.set(false);
        UpdateManifest manifest = latestVerifiedManifest.get();
        if (manifest == null) {
            // Trigger checkUpdate to discover latest verified manifest
            Map<String, Object> check = checkUpdate(true);
            if (!Boolean.TRUE.equals(check.get("updateAvailable"))) {
                setProgress("error", 0, "No new update available to install.");
                return false;
            }
            manifest = latestVerifiedManifest.get();
            if (manifest == null) {
                setProgress("error", 0, "Failed to obtain verified update manifest.");
                return false;
            }
        }

        if (updateInProgress.get()) {
            return false;
        }

        final UpdateManifest targetManifest = manifest;
        Thread downloadThread = new Thread(() -> executeUpdatePipeline(targetManifest), "ota-updater-thread");
        downloadThread.setDaemon(true);
        downloadThread.start();
        return true;
    }

    public boolean applyUpdate() {
        return startUpdateAsync();
    }

    public boolean cancelUpdate() {
        if (!updateInProgress.get()) {
            return false;
        }
        String currentStatus = progressStatus.get();
        if ("restarting".equalsIgnoreCase(currentStatus) || "installing".equalsIgnoreCase(currentStatus)) {
            return false; // Safe boundary: cannot cancel once restart or installation handoff has committed
        }

        cancelRequested.set(true);
        HttpURLConnection conn = activeDownloadConnection;
        if (conn != null) {
            try { conn.disconnect(); } catch (Exception ignored) {}
        }

        try {
            Path txDir = getTxDirectory();
            Files.deleteIfExists(txDir.resolve("rupeecrm-update.war.part"));
            Files.deleteIfExists(txDir.resolve("rupeecrm-update.war"));
            Files.deleteIfExists(getHandoffFilePath());
            Path stateFile = getStateFilePath();
            if (Files.exists(stateFile)) {
                UpdateState state = UpdateState.load(stateFile);
                state.setStatus(UpdateState.STATUS_DOWNLOAD_CANCELLED);
                state.setErrorMessage("Update was cancelled by the user.");
                state.saveAtomic(stateFile);
            }
        } catch (Exception ignored) {}

        setProgress("cancelled", 0, "Update was cancelled by the user.");
        updateInProgress.set(false);
        return true;
    }

    private void executeUpdatePipeline(UpdateManifest manifest) {
        if (!updateInProgress.compareAndSet(false, true)) {
            setProgress("error", 0, "An update transaction is already in progress.");
            return;
        }

        Path lockFile = getLockFilePath();
        try (UpdateLockManager.LockHandle lock = UpdateLockManager.tryAcquire(lockFile)) {
            if (lock == null) {
                setProgress("error", 0, "Update lock contention: another update process is running.");
                return;
            }

            Path txDir = getTxDirectory();
            Files.createDirectories(txDir);

            // Clean up any stale partial files
            Path partFile = txDir.resolve("rupeecrm-update.war.part");
            Files.deleteIfExists(partFile);

            Path manifestArtifact = txDir.resolve(manifest.getArtifactFilename());
            Files.deleteIfExists(manifestArtifact);

            Path finalWar = txDir.resolve("rupeecrm-update.war");
            Files.deleteIfExists(finalWar);

            // Initialize durable state
            UpdateState state = new UpdateState();
            state.setStatus(UpdateState.STATUS_DOWNLOADING);
            state.setCurrentVersion(getCurrentVersion());
            state.setTargetVersion(manifest.getAppVersion());
            state.setExpectedSha256(manifest.getArtifactSha256());
            state.setExpectedSizeBytes(manifest.getArtifactSizeBytes());
            state.saveAtomic(getStateFilePath());

            setProgress("downloading", 0, "Starting download...", manifest.getAppVersion());

            // 1. Download artifact to .part file with progress monitoring
            downloadArtifactToPart(manifest.getArtifactUrl(), partFile, manifest.getArtifactSizeBytes(), manifest.getAppVersion());

            if (cancelRequested.get()) {
                Files.deleteIfExists(partFile);
                setProgress("cancelled", 0, "Update was cancelled by the user.");
                state.setStatus(UpdateState.STATUS_DOWNLOAD_CANCELLED);
                state.setErrorMessage("Update was cancelled by the user.");
                state.saveAtomic(getStateFilePath());
                return;
            }

            // 2. Validate artifact size
            long actualSize = Files.size(partFile);
            if (actualSize != manifest.getArtifactSizeBytes()) {
                Files.deleteIfExists(partFile);
                throw new IllegalStateException("Downloaded artifact size (" + actualSize + " bytes) does not match manifest (" + manifest.getArtifactSizeBytes() + " bytes)");
            }

            // 3. Validate SHA-256 digest
            setProgress("verifying", 92, "Verifying cryptographic SHA-256 digest...", manifest.getAppVersion());
            String actualHash = UpdateCrypto.computeSha256(partFile);
            if (!manifest.getArtifactSha256().equalsIgnoreCase(actualHash)) {
                Files.deleteIfExists(partFile);
                throw new IllegalStateException("Artifact SHA-256 digest mismatch (expected: " + manifest.getArtifactSha256() + ", actual: " + actualHash + ")");
            }

            // 4. Validate ZIP / WAR format magic bytes
            setProgress("verifying", 95, "Verifying package structure...", manifest.getAppVersion());
            if (!UpdateCrypto.isZipFile(partFile)) {
                Files.deleteIfExists(partFile);
                throw new IllegalStateException("Downloaded artifact is not a valid ZIP/WAR archive");
            }

            // 5. Atomic promotion of .part to final staged war (guarantee BOTH manifest name and legacy name exist)
            try {
                Files.move(partFile, manifestArtifact, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                Files.move(partFile, manifestArtifact, StandardCopyOption.REPLACE_EXISTING);
            }

            if (!finalWar.equals(manifestArtifact)) {
                try {
                    Files.copy(manifestArtifact, finalWar, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }

            // Save verified manifest and signature into txDir for Launcher independent verification
            byte[] mBytes = latestVerifiedManifestBytes.get();
            if (mBytes == null) {
                mBytes = manifest.toJson().getBytes(StandardCharsets.UTF_8);
            }
            Files.write(txDir.resolve("manifest.json"), mBytes);
            String sig = latestVerifiedSignature.get();
            if (sig != null) {
                Files.writeString(txDir.resolve("manifest.sig"), sig, StandardCharsets.UTF_8);
            }

            // 6. Pre-update Quarantine Database Snapshot
            setProgress("verifying", 97, "Creating pre-update database quarantine snapshot...", manifest.getAppVersion());
            Path quarantineBackup = createQuarantineDatabaseBackup(state.getTransactionId());
            if (quarantineBackup != null) {
                state.setQuarantineBackupPath(quarantineBackup.toAbsolutePath().toString());
            }

            // 7. Update durable journal to VERIFIED_STAGED
            state.setStatus(UpdateState.STATUS_VERIFIED_STAGED);
            state.setStagedArtifactPath(manifestArtifact.toAbsolutePath().toString());
            state.saveAtomic(getStateFilePath());

            if (cancelRequested.get()) {
                Files.deleteIfExists(manifestArtifact);
                Files.deleteIfExists(finalWar);
                setProgress("cancelled", 0, "Update was cancelled by the user.");
                state.setStatus(UpdateState.STATUS_DOWNLOAD_CANCELLED);
                state.setErrorMessage("Update was cancelled by the user.");
                state.saveAtomic(getStateFilePath());
                return;
            }

            // 8. Create handoff token for supervisor
            Path handoff = getHandoffFilePath();
            String handoffData = "transactionId=" + state.getTransactionId() + "\n" +
                    "targetVersion=" + manifest.getAppVersion() + "\n" +
                    "timestamp=" + System.currentTimeMillis() + "\n";
            Files.writeString(handoff, handoffData, StandardCharsets.UTF_8);

            state.setStatus(UpdateState.STATUS_HANDOFF_REQUESTED);
            state.saveAtomic(getStateFilePath());

            // 9. Notify frontend and trigger graceful restart via Exit Code 10
            setProgress("restarting", 100, "Update verified and staged. Restarting service to activate...", manifest.getAppVersion());

            new Thread(() -> {
                try {
                    Thread.sleep(1200);
                    log.info("Requesting supervisor update restart via Exit Code 10");
                    System.exit(10);
                } catch (Exception ignored) {}
            }, "ota-restart-exit-thread").start();

        } catch (java.util.concurrent.CancellationException ce) {
            log.info("Update pipeline cancelled: {}", ce.getMessage());
            setProgress("cancelled", 0, "Update was cancelled by the user.");
            UpdateState state = UpdateState.load(getStateFilePath());
            state.setStatus(UpdateState.STATUS_DOWNLOAD_CANCELLED);
            state.setErrorMessage("Update was cancelled by the user.");
            try {
                state.saveAtomic(getStateFilePath());
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.error("Update pipeline failed: {}", e.getMessage(), e);
            setProgress("error", 0, "Update failed: " + e.getMessage());
            UpdateState state = UpdateState.load(getStateFilePath());
            state.setStatus(UpdateState.STATUS_DOWNLOAD_FAILED);
            state.setErrorMessage(e.getMessage());
            try {
                state.saveAtomic(getStateFilePath());
            } catch (Exception ignored) {}
        } finally {
            updateInProgress.set(false);
        }
    }

    void downloadArtifactToPart(String downloadUrl, Path partFile, long expectedSize, String targetVersion) throws Exception {
        String currentUrl = downloadUrl;
        HttpURLConnection conn = null;
        int redirectCount = 0;
        final int MAX_REDIRECTS = 5;

        try {
            while (true) {
                if (cancelRequested.get()) {
                    throw new java.util.concurrent.CancellationException("Update download cancelled by user");
                }
                URI uri = new URI(currentUrl);
                String scheme = uri.getScheme();
                if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
                    throw new SecurityException("Insecure or unsupported download URL scheme: " + currentUrl);
                }
                String host = uri.getHost();
                if ("http".equalsIgnoreCase(scheme) && host != null && !host.equals("127.0.0.1") && !host.equals("localhost") && !host.equals("::1")) {
                    throw new SecurityException("Plaintext HTTP downloads are prohibited for non-loopback hosts: " + currentUrl);
                }

                URL url = uri.toURL();
                conn = (HttpURLConnection) url.openConnection();
                this.activeDownloadConnection = conn;
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(30_000);
                conn.setRequestProperty("User-Agent", "RupeeCRM-Updater");
                conn.setInstanceFollowRedirects(false); // Handle redirects explicitly for security
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308) {

                    redirectCount++;
                    if (redirectCount > MAX_REDIRECTS) {
                        throw new IllegalStateException("Too many redirects (exceeded " + MAX_REDIRECTS + ")");
                    }
                    String newLocation = conn.getHeaderField("Location");
                    if (newLocation == null || newLocation.isBlank()) {
                        throw new IllegalStateException("Redirect HTTP " + responseCode + " missing Location header");
                    }
                    URI newUri = uri.resolve(newLocation);
                    String newScheme = newUri.getScheme();
                    if ("http".equalsIgnoreCase(newScheme) && !"http".equalsIgnoreCase(scheme)) {
                        throw new SecurityException("Insecure redirect downgrade from HTTPS to HTTP: " + newUri);
                    }
                    currentUrl = newUri.toString();
                    conn.disconnect();
                    continue;
                }

                if (responseCode < 200 || responseCode >= 300) {
                    throw new IllegalStateException("Failed to download update artifact (HTTP " + responseCode + ")");
                }
                break;
            }

            long totalBytesRead = 0;
            long startTime = System.currentTimeMillis();
            long lastUpdate = startTime;

            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(partFile)) {

                byte[] buffer = new byte[32768];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (cancelRequested.get()) {
                        throw new java.util.concurrent.CancellationException("Update download cancelled by user");
                    }
                    out.write(buffer, 0, read);
                    totalBytesRead += read;

                    if (totalBytesRead > UpdateManifest.MAX_ARTIFACT_SIZE_BYTES) {
                        throw new IllegalStateException("Artifact exceeded maximum allowed size of 250MB");
                    }

                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 200) {
                        lastUpdate = now;
                        int percent = (expectedSize > 0) ? (int) (totalBytesRead * 90 / expectedSize) : 0;
                        long elapsed = Math.max(1, (now - startTime) / 1000);
                        double mb = totalBytesRead / (1024.0 * 1024.0);
                        double speed = mb / elapsed;

                        String speedStr = String.format("%.2f MB/s", speed);
                        String sizeStr = String.format("%.1f MB / %.1f MB", mb, expectedSize / (1024.0 * 1024.0));
                        setProgress("downloading", percent, "Downloading update...", targetVersion, speedStr, sizeStr);
                    }
                }
            }
            setProgress("downloading", 90, "Download complete", targetVersion);
        } catch (java.util.concurrent.CancellationException ce) {
            Files.deleteIfExists(partFile);
            throw ce;
        } catch (Exception e) {
            Files.deleteIfExists(partFile);
            throw e;
        } finally {
            this.activeDownloadConnection = null;
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private Path createQuarantineDatabaseBackup(String txId) {
        if (dataSource == null) {
            return null;
        }
        try {
            File backupsDir = DataDirectoryResolver.resolveBackupsDirectory();
            Path targetZip = backupsDir.toPath().resolve("pre-update-backup-" + txId + ".zip");
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                String escapedPath = targetZip.toFile().getAbsolutePath().replace("'", "''");
                stmt.execute("BACKUP TO '" + escapedPath + "'");
            }
            if (Files.exists(targetZip) && Files.size(targetZip) > 0) {
                log.info("Created pre-update quarantine database backup: {}", targetZip);
                return targetZip;
            }
        } catch (Exception e) {
            log.warn("Non-fatal: could not create quarantine database backup: {}", e.getMessage());
        }
        return null;
    }

    public Map<String, Object> checkForUpdateComplete() {
        Map<String, Object> result = new HashMap<>();
        Path stateFile = getStateFilePath();
        if (Files.exists(stateFile)) {
            UpdateState state = UpdateState.load(stateFile);
            if (UpdateState.STATUS_COMMITTED.equals(state.getStatus()) || UpdateState.STATUS_SUCCESS.equals(state.getStatus())) {
                String updatedVer = (state.getTargetVersion() != null && !state.getTargetVersion().isEmpty())
                        ? state.getTargetVersion() : state.getCurrentVersion();

                result.put("justUpdated", true);
                result.put("justRolledBack", false);
                result.put("updatedVersion", updatedVer);
                result.put("currentVersion", updatedVer);
                result.put("status", "SUCCESS");
                state.setCurrentVersion(updatedVer);
                state.setTargetVersion("");
                state.setStatus(UpdateState.STATUS_IDLE);
                try {
                    state.saveAtomic(stateFile);
                } catch (Exception ignored) {}
                return result;
            } else if (UpdateState.STATUS_ROLLED_BACK.equals(state.getStatus()) || UpdateState.STATUS_VERSION_MISMATCH.equals(state.getStatus())) {
                result.put("justUpdated", false);
                result.put("justRolledBack", true);
                result.put("rolledBack", true);
                result.put("currentVersion", state.getCurrentVersion());
                result.put("failedVersion", state.getTargetVersion());
                result.put("errorMessage", state.getErrorMessage());
                result.put("quarantineBackupPath", state.getQuarantineBackupPath());
                result.put("status", "ROLLED_BACK");
                state.setTargetVersion("");
                state.setStatus(UpdateState.STATUS_IDLE);
                try {
                    state.saveAtomic(stateFile);
                } catch (Exception ignored) {}
                return result;
            }
        }
        result.put("justUpdated", false);
        result.put("justRolledBack", false);
        result.put("currentVersion", getCurrentVersion());
        result.put("status", "IDLE");
        return result;
    }

    public SseEmitter createProgressEmitter() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        this.progressEmitter = emitter;
        emitter.onCompletion(() -> this.progressEmitter = null);
        emitter.onError(e -> this.progressEmitter = null);
        try {
            emitter.send(SseEmitter.event().name("connected").data("{}"));
            if (!"idle".equals(progressStatus.get())) {
                emitter.send(SseEmitter.event().name("progress").data(getProgress(), MediaType.APPLICATION_JSON));
            }
        } catch (Exception ignored) {
            this.progressEmitter = null;
        }
        return emitter;
    }

    public void setProgressEmitter(SseEmitter emitter) {
        this.progressEmitter = emitter;
    }

    public Map<String, Object> getProgress() {
        Map<String, Object> p = new HashMap<>();
        p.put("status", progressStatus.get());
        p.put("percent", progressPercent.get());
        p.put("message", progressMessage.get());
        p.put("latestVersion", progressLatestVersion.get());
        p.put("speed", progressSpeed.get());
        p.put("size", progressSize.get());
        return p;
    }

    private void setProgress(String status, int percent, String message) {
        setProgress(status, percent, message, progressLatestVersion.get(), "", "");
    }

    private void setProgress(String status, int percent, String message, String latestVersion) {
        setProgress(status, percent, message, latestVersion, "", "");
    }

    private void setProgress(String status, int percent, String message, String latestVersion, String speed, String size) {
        if (status != null) progressStatus.set(status);
        progressPercent.set(percent);
        if (message != null) progressMessage.set(message);
        if (latestVersion != null) progressLatestVersion.set(latestVersion);
        if (speed != null) progressSpeed.set(speed);
        if (size != null) progressSize.set(size);
        sendProgressEvent();
    }

    private void sendProgressEvent() {
        SseEmitter emitter = this.progressEmitter;
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(getProgress(), MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                this.progressEmitter = null;
            }
        }
    }

    private byte[] fetchUrlBytes(String urlString, int connectTimeout, int readTimeout, int maxBytes) throws Exception {
        URL url = new URI(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setRequestProperty("User-Agent", "RupeeCRM-Updater");
        conn.connect();

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + " from " + urlString);
        }

        try (InputStream in = conn.getInputStream()) {
            byte[] bytes = in.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) {
                throw new IllegalStateException("Response from " + urlString + " exceeded limit of " + maxBytes + " bytes");
            }
            return bytes;
        }
    }

    private String fetchUrlString(String urlString, int connectTimeout, int readTimeout) {
        try {
            byte[] bytes = fetchUrlBytes(urlString, connectTimeout, readTimeout, 100_000);
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static int compareVersions(String v1, String v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;

        String norm1 = v1.replaceAll("^[vV]", "").trim();
        String norm2 = v2.replaceAll("^[vV]", "").trim();

        String[] split1 = norm1.split("[-+]", 2);
        String[] split2 = norm2.split("[-+]", 2);

        String[] parts1 = split1[0].split("\\.");
        String[] parts2 = split2[0].split("\\.");

        int maxLen = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLen; i++) {
            long num1 = 0;
            long num2 = 0;
            if (i < parts1.length && !parts1[i].trim().isEmpty()) {
                try { num1 = Long.parseLong(parts1[i].trim().replaceAll("[^0-9]", "")); } catch (NumberFormatException ignored) {}
            }
            if (i < parts2.length && !parts2[i].trim().isEmpty()) {
                try { num2 = Long.parseLong(parts2[i].trim().replaceAll("[^0-9]", "")); } catch (NumberFormatException ignored) {}
            }
            if (num1 != num2) {
                return Long.compare(num1, num2);
            }
        }

        boolean hasSuffix1 = split1.length > 1 && !split1[1].isEmpty();
        boolean hasSuffix2 = split2.length > 1 && !split2[1].isEmpty();

        if (!hasSuffix1 && hasSuffix2) return 1;
        if (hasSuffix1 && !hasSuffix2) return -1;
        if (hasSuffix1 && hasSuffix2) return split1[1].compareToIgnoreCase(split2[1]);

        return 0;
    }

    public static String extractCustomerChangelog(String rawBody) {
        if (rawBody == null || rawBody.trim().isEmpty()) return "";
        return rawBody.trim();
    }
}
