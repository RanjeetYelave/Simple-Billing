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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.PublicKey;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Enterprise-grade OTA update engine for RupeeCRM.
 * Enforces cryptographic Ed25519 manifest verification, resumable partial downloads,
 * OS-level transaction locking, online H2 database quarantine snapshots, and safe handoff.
 */
@Service
public class UpdateService {

    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);

    public static final String DEFAULT_PUBLIC_KEY = "MCowBQYDK2VwAyEAtdG8eJgrX0L7jtx13wOGnqSeeID2B/aRv1+OZQ7v3C8=";
    public static final long MIN_FREE_DISK_SPACE_BYTES = 250L * 1024 * 1024; // 250 MB free space required

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
    private final AtomicReference<String> progressEta = new AtomicReference<>("");
    private final AtomicBoolean updateInProgress = new AtomicBoolean(false);
    private final AtomicBoolean pauseRequested = new AtomicBoolean(false);
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

    public static class DownloadPausedException extends Exception {
        public DownloadPausedException(String message) {
            super(message);
        }
    }

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

    public void setDefaultVersion(String defaultVersion) {
        this.defaultVersion = defaultVersion;
    }

    public boolean hasSufficientDiskSpace() {
        try {
            File stagingDir = getStagingDirectory().toFile();
            if (!stagingDir.exists()) {
                stagingDir.mkdirs();
            }
            long usableSpace = stagingDir.getUsableSpace();
            return usableSpace >= MIN_FREE_DISK_SPACE_BYTES;
        } catch (Exception e) {
            log.warn("Could not check usable disk space: {}", e.getMessage());
            return true;
        }
    }

    public String getAuthoritativeBinaryVersion() {
        Package pkg = UpdateService.class.getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null && !pkg.getImplementationVersion().isBlank()) {
            return pkg.getImplementationVersion().trim();
        }
        if (defaultVersion != null && !defaultVersion.isBlank()) {
            return defaultVersion.trim();
        }
        return "v1.0.0";
    }

    public String getCurrentVersion() {
        // 1. Check for .billsoft-version metadata override file
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
            } catch (Exception ignored) {
            }
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
                    Path codePath = Path
                            .of(UpdateService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                    Path codeDir = Files.isDirectory(codePath) ? codePath : codePath.getParent();
                    if (codeDir != null) {
                        candidateVersionFiles.add(codeDir.resolve(".billsoft-version"));
                        if (codeDir.getParent() != null) {
                            candidateVersionFiles.add(codeDir.getParent().resolve(".billsoft-version"));
                        }
                    }
                }
            } catch (Exception ignored) {
            }

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

        // 2. Authoritative running binary version
        String authoritativeVersion = getAuthoritativeBinaryVersion();

        // 3. Inspect durable update-state.json and reconcile stale or contradictory metadata
        try {
            Path stateFile = getStateFilePath();
            if (Files.exists(stateFile)) {
                UpdateState state = UpdateState.load(stateFile);
                if (state != null) {
                    String stateVer = state.getCurrentVersion();
                    String targetVer = state.getTargetVersion();
                    boolean isStale = false;

                    if (stateVer != null && !stateVer.isBlank() && compareVersions(authoritativeVersion, stateVer) != 0) {
                        isStale = true;
                    }
                    if (targetVer != null && !targetVer.isBlank() && compareVersions(authoritativeVersion, targetVer) < 0
                            && (UpdateState.STATUS_COMMITTED.equals(state.getStatus()) || UpdateState.STATUS_SUCCESS.equals(state.getStatus()) || UpdateState.STATUS_UPDATE_SUCCESS.equals(state.getStatus()))) {
                        isStale = true;
                    }

                    if (isStale) {
                        log.warn("Detected stale update-state metadata (recorded current: {}, recorded target: {}, actual running binary: {}). Reconciling.",
                                stateVer, targetVer, authoritativeVersion);
                        state.setCurrentVersion(authoritativeVersion);
                        if (targetVer != null && compareVersions(authoritativeVersion, targetVer) >= 0) {
                            state.setStatus(UpdateState.STATUS_COMMITTED);
                        }
                        try {
                            state.saveAtomic(stateFile);
                        } catch (Exception ignored) {}
                        return authoritativeVersion;
                    } else if (stateVer != null && !stateVer.isBlank() && compareVersions(authoritativeVersion, stateVer) == 0) {
                        return stateVer;
                    } else if (state.getStatus() != null && (UpdateState.STATUS_COMMITTED.equals(state.getStatus()) || UpdateState.STATUS_SUCCESS.equals(state.getStatus()) || UpdateState.STATUS_UPDATE_SUCCESS.equals(state.getStatus()))) {
                        if (targetVer != null && compareVersions(authoritativeVersion, targetVer) >= 0 && !authoritativeVersion.equalsIgnoreCase(stateVer)) {
                            state.setCurrentVersion(authoritativeVersion);
                            try { state.saveAtomic(stateFile); } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Note: Could not inspect or reconcile update-state file: {}", e.getMessage());
        }

        return authoritativeVersion;
    }

    public synchronized Map<String, Object> checkUpdate() {
        return checkUpdate(false);
    }

    private String[] resolveReleaseManifestUrls() {
        String mUrl = this.manifestUrl;
        String sUrl = this.signatureUrl;

        if (mUrl != null && mUrl.contains("github.com/RanjeetYelave/Simple-Billing/releases")) {
            try {
                String apiUrl = "https://api.github.com/repos/RanjeetYelave/Simple-Billing/releases?per_page=5";
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.set("User-Agent", "RupeeCRM-Updater");
                headers.set("Accept", "application/vnd.github.v3+json");
                org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);

                org.springframework.http.ResponseEntity<List> resp = restTemplate.exchange(
                        apiUrl, org.springframework.http.HttpMethod.GET, entity, List.class);

                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    for (Object item : resp.getBody()) {
                        if (item instanceof Map<?, ?> rel) {
                            if (Boolean.TRUE.equals(rel.get("draft")) || Boolean.TRUE.equals(rel.get("prerelease"))) {
                                continue;
                            }
                            String[] found = extractManifestUrls(rel);
                            if (found != null) {
                                return found;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("GitHub Releases API discovery unavailable ({}), using direct manifest URL", e.getMessage());
            }
        }
        return new String[] { mUrl, sUrl };
    }

    private String[] extractManifestUrls(Map<?, ?> rel) {
        Object assetsObj = rel.get("assets");
        if (assetsObj instanceof List<?> assets) {
            String foundM = null;
            String foundS = null;
            for (Object a : assets) {
                if (a instanceof Map<?, ?> asset) {
                    Object name = asset.get("name");
                    Object downloadUrl = asset.get("browser_download_url");
                    if ("manifest.json".equals(name) && downloadUrl != null) {
                        foundM = downloadUrl.toString();
                    } else if ("manifest.sig".equals(name) && downloadUrl != null) {
                        foundS = downloadUrl.toString();
                    }
                }
            }
            if (foundM != null && foundS != null) {
                return new String[] { foundM, foundS };
            }
        }
        return null;
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

        if (!updateCheckEnabled || "true".equalsIgnoreCase(System.getenv("DISABLE_UPDATE_CHECK"))
                || "false".equalsIgnoreCase(System.getProperty("app.update.check.enabled"))) {
            response.put("updateAvailable", false);
            response.put("message", "Update check is disabled by configuration");
            return response;
        }

        try {
            String[] urls = resolveReleaseManifestUrls();
            String effectiveManifestUrl = urls[0];
            String effectiveSignatureUrl = urls[1];

            // 1. Fetch manifest.json
            byte[] manifestBytes = restTemplate.getForObject(effectiveManifestUrl, byte[].class);
            if (manifestBytes == null || manifestBytes.length == 0) {
                throw new IllegalStateException("Empty manifest received from " + effectiveManifestUrl);
            }

            // 2. Fetch manifest.sig
            String sigBase64 = restTemplate.getForObject(effectiveSignatureUrl, String.class);
            if (sigBase64 == null || sigBase64.trim().isEmpty()) {
                throw new IllegalStateException("Empty signature received from " + effectiveSignatureUrl);
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
     * Initiates or resumes the update download asynchronously.
     */
    public boolean startUpdateAsync() {
        pauseRequested.set(false);
        cancelRequested.set(false);

        UpdateManifest manifest = latestVerifiedManifest.get();
        if (manifest == null) {
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
        Path stateFile = getStateFilePath();
        if (Files.exists(stateFile)) {
            UpdateState state = UpdateState.load(stateFile);
            if (UpdateState.STATUS_READY_TO_INSTALL.equals(state.getStatus()) ||
                UpdateState.STATUS_VERIFIED_STAGED.equals(state.getStatus())) {
                return installStagedUpdate();
            }
        }
        return startUpdateAsync();
    }

    public boolean pauseUpdate() {
        String currentStatus = progressStatus.get();
        if ("restarting".equalsIgnoreCase(currentStatus) || "installing".equalsIgnoreCase(currentStatus) || "ready_to_install".equalsIgnoreCase(currentStatus)) {
            return false;
        }

        pauseRequested.set(true);
        try {
            UpdateState state = UpdateState.load(getStateFilePath());
            state.setStatus(UpdateState.STATUS_DOWNLOAD_PAUSED);
            Path partFile = getTxDirectory().resolve("rupeecrm-update.war.part");
            if (Files.exists(partFile)) {
                state.setBytesDownloaded(Files.size(partFile));
            }
            state.saveAtomic(getStateFilePath());
        } catch (Exception ignored) {}

        HttpURLConnection conn = activeDownloadConnection;
        if (conn != null) {
            try {
                conn.disconnect();
            } catch (Exception ignored) {}
        }
        return true;
    }

    public boolean resumeUpdate() {
        if (updateInProgress.get()) {
            return false;
        }
        pauseRequested.set(false);
        cancelRequested.set(false);
        return startUpdateAsync();
    }

    public boolean retryUpdate() {
        if (updateInProgress.get()) {
            return false;
        }
        pauseRequested.set(false);
        cancelRequested.set(false);
        return startUpdateAsync();
    }

    public boolean cancelUpdate() {
        if (!updateInProgress.get()) {
            return false;
        }
        String currentStatus = progressStatus.get();
        if ("restarting".equalsIgnoreCase(currentStatus) || "installing".equalsIgnoreCase(currentStatus)) {
            return false;
        }

        cancelRequested.set(true);
        HttpURLConnection conn = activeDownloadConnection;
        if (conn != null) {
            try {
                conn.disconnect();
            } catch (Exception ignored) {}
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

    /**
     * Executes atomic update handoff and requests supervisor service restart via Exit Code 10.
     * Decoupled from downloading so billing operations are never interrupted unexpectedly.
     */
    public synchronized boolean installStagedUpdate() {
        if (updateInProgress.get()) {
            return false;
        }
        Path txDir = getTxDirectory();
        Path manifestFile = txDir.resolve("manifest.json");
        Path sigFile = txDir.resolve("manifest.sig");
        Path stagedWar = txDir.resolve("rupeecrm-update.war");
        Path stateFile = getStateFilePath();

        if (!Files.exists(manifestFile) || !Files.exists(sigFile) || !Files.exists(stagedWar) || !Files.exists(stateFile)) {
            log.error("Cannot install update: Staged update files or state not found in {}", txDir);
            return false;
        }

        try {
            UpdateState state = UpdateState.load(stateFile);
            Path handoff = getHandoffFilePath();
            String handoffData = "transactionId=" + state.getTransactionId() + "\n" +
                    "targetVersion=" + state.getTargetVersion() + "\n" +
                    "timestamp=" + System.currentTimeMillis() + "\n";
            Files.writeString(handoff, handoffData, StandardCharsets.UTF_8);

            state.setStatus(UpdateState.STATUS_APPLICATION_SHUTDOWN_PENDING);
            state.saveAtomic(stateFile);

            setProgress("restarting", 100, "Update verified and staged. Restarting service to activate...", state.getTargetVersion());

            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    log.info("Requesting supervisor update restart via Exit Code 10");
                    System.exit(10);
                } catch (Exception ignored) {}
            }, "ota-install-exit-thread").start();

            return true;
        } catch (Exception e) {
            log.error("Failed to trigger update installation: {}", e.getMessage(), e);
            return false;
        }
    }

    private void executeUpdatePipeline(UpdateManifest manifest) {
        if (!updateInProgress.compareAndSet(false, true)) {
            setProgress("error", 0, "An update transaction is already in progress.");
            return;
        }

        // Verify available disk space before proceeding
        if (!hasSufficientDiskSpace()) {
            long freeMb = 0L;
            try {
                freeMb = getStagingDirectory().toFile().getUsableSpace() / (1024 * 1024);
            } catch (Exception ignored) {}
            String msg = "Insufficient disk space: " + freeMb + " MB available, but at least 250 MB is required for safe updates.";
            log.error(msg);
            setProgress("error", 0, msg);
            try {
                UpdateState state = UpdateState.load(getStateFilePath());
                state.setStatus(UpdateState.STATUS_UPDATE_FAILED);
                state.setErrorCategory("DISK_SPACE");
                state.setErrorMessage(msg);
                state.saveAtomic(getStateFilePath());
            } catch (Exception ignored) {}
            updateInProgress.set(false);
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

            Path partFile = txDir.resolve("rupeecrm-update.war.part");
            Path manifestArtifact = txDir.resolve(manifest.getArtifactFilename());
            Path finalWar = txDir.resolve("rupeecrm-update.war");

            // Inspect existing partial file and state
            UpdateState state = UpdateState.load(getStateFilePath());
            boolean canResume = false;
            if (manifest.getAppVersion().equals(state.getTargetVersion())
                    && manifest.getArtifactSha256().equalsIgnoreCase(state.getExpectedSha256())
                    && Files.exists(partFile)) {
                canResume = true;
            } else {
                Files.deleteIfExists(partFile);
                Files.deleteIfExists(manifestArtifact);
                Files.deleteIfExists(finalWar);
                state = new UpdateState();
                state.setCurrentVersion(getCurrentVersion());
                state.setTargetVersion(manifest.getAppVersion());
                state.setExpectedSha256(manifest.getArtifactSha256());
                state.setExpectedSizeBytes(manifest.getArtifactSizeBytes());
                state.setDownloadUrl(manifest.getArtifactUrl());
            }

            state.setStatus(UpdateState.STATUS_DOWNLOADING);
            state.saveAtomic(getStateFilePath());

            int initPercent = (canResume && manifest.getArtifactSizeBytes() > 0)
                    ? (int) (Files.size(partFile) * 90 / manifest.getArtifactSizeBytes()) : 0;
            setProgress("downloading", initPercent, canResume ? "Resuming update download..." : "Starting download...",
                    manifest.getAppVersion());

            // 1. Resumable download loop
            downloadArtifactToPart(manifest.getArtifactUrl(), partFile, manifest.getArtifactSizeBytes(),
                    manifest.getAppVersion());

            if (pauseRequested.get()) {
                log.info("Update download paused by user. Preserving partial file for resumption.");
                return;
            }

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
                throw new IllegalStateException("Downloaded artifact size (" + actualSize
                        + " bytes) does not match manifest (" + manifest.getArtifactSizeBytes() + " bytes)");
            }

            // 3. Validate SHA-256 digest
            state.setStatus(UpdateState.STATUS_DOWNLOAD_VERIFYING);
            state.saveAtomic(getStateFilePath());
            setProgress("verifying", 92, "Verifying cryptographic SHA-256 digest...", manifest.getAppVersion());
            String actualHash = UpdateCrypto.computeSha256(partFile);
            if (!manifest.getArtifactSha256().equalsIgnoreCase(actualHash)) {
                Files.deleteIfExists(partFile);
                throw new IllegalStateException("Artifact SHA-256 digest mismatch (expected: "
                        + manifest.getArtifactSha256() + ", actual: " + actualHash + ")");
            }

            // 4. Validate ZIP / WAR format magic bytes
            setProgress("verifying", 95, "Verifying package structure...", manifest.getAppVersion());
            if (!UpdateCrypto.isZipFile(partFile)) {
                Files.deleteIfExists(partFile);
                throw new IllegalStateException("Downloaded artifact is not a valid ZIP/WAR archive");
            }

            // 5. Promote .part to final staged war
            try {
                Files.move(partFile, manifestArtifact, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                Files.move(partFile, manifestArtifact, StandardCopyOption.REPLACE_EXISTING);
            }

            if (!finalWar.equals(manifestArtifact)) {
                try {
                    Files.copy(manifestArtifact, finalWar, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }

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
            state.setStatus(UpdateState.STATUS_BACKUP_IN_PROGRESS);
            state.saveAtomic(getStateFilePath());
            setProgress("verifying", 97, "Creating pre-update database quarantine snapshot...",
                    manifest.getAppVersion());
            Path quarantineBackup = createQuarantineDatabaseBackup(state.getTransactionId());
            if (quarantineBackup != null) {
                state.setQuarantineBackupPath(quarantineBackup.toAbsolutePath().toString());
            }

            // 7. Transition durable state to READY_TO_INSTALL
            state.setStatus(UpdateState.STATUS_READY_TO_INSTALL);
            state.setStagedArtifactPath(manifestArtifact.toAbsolutePath().toString());
            state.setBytesDownloaded(actualSize);
            state.setRetryCount(0);
            state.setErrorMessage("");
            state.saveAtomic(getStateFilePath());

            setProgress("ready_to_install", 100, "Update v" + manifest.getAppVersion() + " is ready to install.",
                    manifest.getAppVersion());
            log.info("Update package v{} staged and READY_TO_INSTALL. User can install now or postpone.", manifest.getAppVersion());

        } catch (DownloadPausedException de) {
            log.info("Update pipeline paused: {}", de.getMessage());
        } catch (java.util.concurrent.CancellationException ce) {
            log.info("Update pipeline cancelled: {}", ce.getMessage());
            setProgress("cancelled", 0, "Update was cancelled by the user.");
            try {
                UpdateState state = UpdateState.load(getStateFilePath());
                state.setStatus(UpdateState.STATUS_DOWNLOAD_CANCELLED);
                state.setErrorMessage("Update was cancelled by the user.");
                state.saveAtomic(getStateFilePath());
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.error("Update pipeline failed: {}", e.getMessage(), e);
            setProgress("error", 0, "Update failed: " + e.getMessage());
            try {
                UpdateState state = UpdateState.load(getStateFilePath());
                state.setStatus(UpdateState.STATUS_UPDATE_FAILED);
                state.setErrorMessage(e.getMessage());
                state.saveAtomic(getStateFilePath());
            } catch (Exception ignored) {}
        } finally {
            updateInProgress.set(false);
        }
    }

    void downloadArtifactToPart(String downloadUrl, Path partFile, long expectedSize, String targetVersion)
            throws Exception {
        String currentUrl = downloadUrl;
        int maxRetries = 5;
        int retryCount = 0;

        while (true) {
            HttpURLConnection conn = null;
            try {
                if (pauseRequested.get()) {
                    throw new DownloadPausedException("Update download paused by user");
                }
                if (cancelRequested.get()) {
                    throw new java.util.concurrent.CancellationException("Update download cancelled by user");
                }

                long existingBytes = 0L;
                if (Files.exists(partFile)) {
                    existingBytes = Files.size(partFile);
                }

                if (existingBytes > 0 && expectedSize > 0) {
                    if (existingBytes == expectedSize) {
                        log.info("Existing partial file size ({} bytes) equals expected size ({} bytes).", existingBytes, expectedSize);
                        return;
                    } else if (existingBytes > expectedSize) {
                        log.warn("Existing partial file size ({} bytes) exceeds expected size ({} bytes). Resetting.", existingBytes, expectedSize);
                        Files.deleteIfExists(partFile);
                        existingBytes = 0L;
                    }
                }

                Map<String, String> requestHeaders = new HashMap<>();
                requestHeaders.put("User-Agent", "RupeeCRM-Updater");

                UpdateState state = UpdateState.load(getStateFilePath());
                boolean sendingRange = false;
                if (existingBytes > 0) {
                    requestHeaders.put("Range", "bytes=" + existingBytes + "-");
                    sendingRange = true;
                    if (state.getEtag() != null && !state.getEtag().isBlank()) {
                        requestHeaders.put("If-Range", state.getEtag());
                    }
                }

                conn = createDownloadConnection(currentUrl, requestHeaders);
                this.activeDownloadConnection = conn;

                int responseCode = conn.getResponseCode();
                boolean append = false;
                long totalBytesRead = 0;

                if (responseCode == 206) {
                    append = true;
                    totalBytesRead = existingBytes;
                    log.info("HTTP 206 Partial Content received. Resuming at offset {}", existingBytes);
                } else if (responseCode == 200) {
                    append = false;
                    totalBytesRead = 0;
                    if (sendingRange) {
                        log.info("HTTP 200 OK received instead of 206. Restarting download from byte 0.");
                    }
                } else if (responseCode == 416) {
                    log.warn("HTTP 416 Range Not Satisfiable. Resetting partial file and restarting from 0.");
                    Files.deleteIfExists(partFile);
                    conn.disconnect();
                    requestHeaders.remove("Range");
                    requestHeaders.remove("If-Range");
                    conn = createDownloadConnection(currentUrl, requestHeaders);
                    this.activeDownloadConnection = conn;
                    responseCode = conn.getResponseCode();
                    if (responseCode < 200 || responseCode >= 300) {
                        throw new IllegalStateException("Failed to download update after Range 416 reset (HTTP " + responseCode + ")");
                    }
                    append = false;
                    totalBytesRead = 0;
                } else {
                    throw new IllegalStateException("Failed to download update artifact (HTTP " + responseCode + ")");
                }

                String etag = conn.getHeaderField("ETag");
                if (etag != null && !etag.isBlank()) {
                    state.setEtag(etag);
                }
                String lastMod = conn.getHeaderField("Last-Modified");
                if (lastMod != null && !lastMod.isBlank()) {
                    state.setLastModified(lastMod);
                }
                try { state.saveAtomic(getStateFilePath()); } catch (Exception ignored) {}

                long startTime = System.currentTimeMillis();
                long lastProgressTime = startTime;
                long bytesAtIntervalStart = totalBytesRead;

                try (InputStream in = conn.getInputStream();
                     OutputStream out = Files.newOutputStream(partFile,
                             append ? new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE}
                                    : new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE})) {

                    byte[] buffer = new byte[32768];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (pauseRequested.get()) {
                            out.flush();
                            state.setBytesDownloaded(totalBytesRead);
                            state.setStatus(UpdateState.STATUS_DOWNLOAD_PAUSED);
                            try { state.saveAtomic(getStateFilePath()); } catch (Exception ignored) {}
                            int percent = (expectedSize > 0) ? (int) (totalBytesRead * 90 / expectedSize) : 0;
                            double mb = totalBytesRead / (1024.0 * 1024.0);
                            String sizeStr = String.format("%.1f MB / %.1f MB", mb, expectedSize / (1024.0 * 1024.0));
                            setProgress("paused", percent, "Download paused by user", targetVersion, "", sizeStr);
                            throw new DownloadPausedException("Update download paused by user");
                        }
                        if (cancelRequested.get()) {
                            throw new java.util.concurrent.CancellationException("Update download cancelled by user");
                        }

                        out.write(buffer, 0, read);
                        totalBytesRead += read;

                        if (totalBytesRead > UpdateManifest.MAX_ARTIFACT_SIZE_BYTES) {
                            throw new IllegalStateException("Artifact exceeded maximum allowed size of 250MB");
                        }

                        long now = System.currentTimeMillis();
                        if (now - lastProgressTime > 200) {
                            long intervalDuration = Math.max(1, now - lastProgressTime);
                            long bytesThisInterval = totalBytesRead - bytesAtIntervalStart;
                            double speedMbPerSec = (bytesThisInterval / (1024.0 * 1024.0)) / (intervalDuration / 1000.0);

                            lastProgressTime = now;
                            bytesAtIntervalStart = totalBytesRead;

                            int percent = (expectedSize > 0) ? (int) (totalBytesRead * 90 / expectedSize) : 0;
                            double mb = totalBytesRead / (1024.0 * 1024.0);
                            String speedStr = String.format("%.2f MB/s", speedMbPerSec);
                            String sizeStr = String.format("%.1f MB / %.1f MB", mb, expectedSize / (1024.0 * 1024.0));

                            long remainingBytes = Math.max(0, expectedSize - totalBytesRead);
                            String etaStr = "";
                            if (speedMbPerSec > 0.01 && remainingBytes > 0) {
                                long etaSec = (long) ((remainingBytes / (1024.0 * 1024.0)) / speedMbPerSec);
                                long m = etaSec / 60;
                                long s = etaSec % 60;
                                etaStr = m > 0 ? (m + "m " + s + "s") : (s + "s");
                            }

                            setProgress("downloading", percent, "Downloading update...", targetVersion, speedStr, sizeStr, etaStr);
                            state.setBytesDownloaded(totalBytesRead);
                        }
                    }
                }

                setProgress("downloading", 90, "Download complete", targetVersion);
                return;

            } catch (DownloadPausedException | java.util.concurrent.CancellationException e) {
                throw e;
            } catch (Exception e) {
                if (pauseRequested.get()) {
                    try {
                        UpdateState state = UpdateState.load(getStateFilePath());
                        state.setStatus(UpdateState.STATUS_DOWNLOAD_PAUSED);
                        state.setBytesDownloaded(Files.exists(partFile) ? Files.size(partFile) : 0L);
                        state.saveAtomic(getStateFilePath());
                    } catch (Exception ignored) {}
                    throw new DownloadPausedException("Update download paused by user");
                }
                if (cancelRequested.get()) {
                    Files.deleteIfExists(partFile);
                    throw new java.util.concurrent.CancellationException("Update download cancelled by user");
                }

                // Permanent non-retryable failures fail fast without retry delay
                if (e instanceof SecurityException || e instanceof FileNotFoundException ||
                    e instanceof MalformedURLException || e instanceof ConnectException ||
                    e instanceof UnknownHostException) {
                    Files.deleteIfExists(partFile);
                    throw e;
                }

                retryCount++;
                if (retryCount <= maxRetries) {
                    long backoffMs = Math.min(16000L, 1000L * (1L << (retryCount - 1)));
                    log.warn("Download interrupted: {} (retry {}/{} in {}ms)", e.getMessage(), retryCount, maxRetries, backoffMs);

                    try {
                        UpdateState state = UpdateState.load(getStateFilePath());
                        state.setStatus(UpdateState.STATUS_DOWNLOAD_RETRYING);
                        state.setRetryCount(retryCount);
                        state.setNextRetryTime(System.currentTimeMillis() + backoffMs);
                        state.setErrorMessage("Transient network failure: " + e.getMessage());
                        state.saveAtomic(getStateFilePath());
                    } catch (Exception ignored) {}

                    int percent = 0;
                    if (Files.exists(partFile) && expectedSize > 0) {
                        try {
                            percent = (int) (Files.size(partFile) * 90 / expectedSize);
                        } catch (Exception ignored) {}
                    }
                    setProgress("retrying", percent, "Connection lost (" + e.getMessage() + "). Retrying in " + (backoffMs / 1000) + "s...", targetVersion);

                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new java.util.concurrent.CancellationException("Retry interrupted");
                    }
                    continue;
                }

                Files.deleteIfExists(partFile);
                throw e;
            } finally {
                this.activeDownloadConnection = null;
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
            }
        }
    }

    private HttpURLConnection createDownloadConnection(String currentUrl, Map<String, String> requestHeaders) throws Exception {
        int redirectCount = 0;
        final int MAX_REDIRECTS = 5;

        while (true) {
            URI uri = new URI(currentUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
                throw new SecurityException("Insecure or unsupported download URL scheme: " + currentUrl);
            }
            String host = uri.getHost();
            if ("http".equalsIgnoreCase(scheme) && host != null && !host.equals("127.0.0.1")
                    && !host.equals("localhost") && !host.equals("::1")) {
                throw new SecurityException(
                        "Plaintext HTTP downloads are prohibited for non-loopback hosts: " + currentUrl);
            }

            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.setInstanceFollowRedirects(false);

            if (requestHeaders != null) {
                for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            conn.connect();
            int responseCode = conn.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308) {

                redirectCount++;
                if (redirectCount > MAX_REDIRECTS) {
                    conn.disconnect();
                    throw new IllegalStateException("Too many redirects (exceeded " + MAX_REDIRECTS + ")");
                }
                String newLocation = conn.getHeaderField("Location");
                if (newLocation == null || newLocation.isBlank()) {
                    conn.disconnect();
                    throw new IllegalStateException("Redirect HTTP " + responseCode + " missing Location header");
                }
                URI newUri = uri.resolve(newLocation);
                String newScheme = newUri.getScheme();
                if ("http".equalsIgnoreCase(newScheme) && !"http".equalsIgnoreCase(scheme)) {
                    conn.disconnect();
                    throw new SecurityException("Insecure redirect downgrade from HTTPS to HTTP: " + newUri);
                }
                currentUrl = newUri.toString();
                conn.disconnect();
                continue;
            }

            return conn;
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
            if (UpdateState.STATUS_COMMITTED.equals(state.getStatus())
                    || UpdateState.STATUS_SUCCESS.equals(state.getStatus())
                    || UpdateState.STATUS_UPDATE_SUCCESS.equals(state.getStatus())
                    || UpdateState.STATUS_COMPLETED.equals(state.getStatus())) {
                String updatedVer = (state.getTargetVersion() != null && !state.getTargetVersion().isEmpty())
                        ? state.getTargetVersion()
                        : state.getCurrentVersion();

                result.put("justUpdated", true);
                result.put("justRolledBack", false);
                result.put("updatedVersion", updatedVer);
                result.put("currentVersion", updatedVer);
                result.put("status", "SUCCESS");
                this.defaultVersion = updatedVer;
                state.setCurrentVersion(updatedVer);
                state.setTargetVersion("");
                state.setStatus(UpdateState.STATUS_IDLE);
                try {
                    state.saveAtomic(stateFile);
                } catch (Exception ignored) {
                }
                return result;
            } else if (UpdateState.STATUS_ROLLED_BACK.equals(state.getStatus())
                    || UpdateState.STATUS_ROLLBACK_COMPLETED.equals(state.getStatus())
                    || UpdateState.STATUS_VERSION_MISMATCH.equals(state.getStatus())) {
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
                } catch (Exception ignored) {
                }
                return result;
            }
        }
        result.put("justUpdated", false);
        result.put("justRolledBack", false);
        result.put("currentVersion", getCurrentVersion());
        result.put("status", "IDLE");
        return result;
    }

    public Map<String, Object> getUpdateDiagnostics() {
        Map<String, Object> diag = new HashMap<>();
        diag.put("currentVersion", getCurrentVersion());
        diag.put("authoritativeBinaryVersion", getAuthoritativeBinaryVersion());
        diag.put("updateInProgress", updateInProgress.get());
        diag.put("pauseRequested", pauseRequested.get());
        diag.put("cancelRequested", cancelRequested.get());

        Path stateFile = getStateFilePath();
        UpdateState state = Files.exists(stateFile) ? UpdateState.load(stateFile) : new UpdateState();
        diag.put("state", state);
        diag.put("status", state.getStatus());
        diag.put("targetVersion", state.getTargetVersion());
        diag.put("transactionId", state.getTransactionId());
        diag.put("errorCategory", state.getErrorCategory());
        diag.put("errorMessage", state.getErrorMessage());
        diag.put("retryCount", state.getRetryCount());

        Path txDir = getTxDirectory();
        Path partFile = txDir.resolve("rupeecrm-update.war.part");
        Path stagedWar = txDir.resolve("rupeecrm-update.war");
        Path handoffFile = getHandoffFilePath();

        diag.put("partFileExists", Files.exists(partFile));
        try {
            diag.put("partFileSizeBytes", Files.exists(partFile) ? Files.size(partFile) : 0L);
        } catch (Exception ignored) {
            diag.put("partFileSizeBytes", 0L);
        }
        diag.put("stagedWarExists", Files.exists(stagedWar));
        try {
            diag.put("stagedWarSizeBytes", Files.exists(stagedWar) ? Files.size(stagedWar) : 0L);
        } catch (Exception ignored) {
            diag.put("stagedWarSizeBytes", 0L);
        }
        diag.put("handoffPending", Files.exists(handoffFile));

        long usableSpace = 0L;
        try {
            usableSpace = getStagingDirectory().toFile().getUsableSpace();
        } catch (Exception ignored) {}
        diag.put("freeDiskSpaceBytes", usableSpace);
        diag.put("freeDiskSpaceMB", usableSpace / (1024 * 1024));
        diag.put("hasSufficientDiskSpace", hasSufficientDiskSpace());

        diag.put("progress", getProgress());
        return diag;
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
        p.put("eta", progressEta.get());
        return p;
    }

    private void setProgress(String status, int percent, String message) {
        setProgress(status, percent, message, progressLatestVersion.get(), "", "", "");
    }

    private void setProgress(String status, int percent, String message, String latestVersion) {
        setProgress(status, percent, message, latestVersion, "", "", "");
    }

    private void setProgress(String status, int percent, String message, String latestVersion, String speed,
                             String size) {
        setProgress(status, percent, message, latestVersion, speed, size, "");
    }

    private void setProgress(String status, int percent, String message, String latestVersion, String speed,
                             String size, String eta) {
        if (status != null)
            progressStatus.set(status);
        progressPercent.set(percent);
        if (message != null)
            progressMessage.set(message);
        if (latestVersion != null)
            progressLatestVersion.set(latestVersion);
        if (speed != null)
            progressSpeed.set(speed);
        if (size != null)
            progressSize.set(size);
        if (eta != null)
            progressEta.set(eta);
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

    public static int compareVersions(String v1, String v2) {
        if (v1 == null && v2 == null)
            return 0;
        if (v1 == null)
            return -1;
        if (v2 == null)
            return 1;

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
                try {
                    num1 = Long.parseLong(parts1[i].trim().replaceAll("[^0-9]", ""));
                } catch (NumberFormatException ignored) {
                }
            }
            if (i < parts2.length && !parts2[i].trim().isEmpty()) {
                try {
                    num2 = Long.parseLong(parts2[i].trim().replaceAll("[^0-9]", ""));
                } catch (NumberFormatException ignored) {
                }
            }
            if (num1 != num2) {
                return Long.compare(num1, num2);
            }
        }

        boolean hasSuffix1 = split1.length > 1 && !split1[1].isEmpty();
        boolean hasSuffix2 = split2.length > 1 && !split2[1].isEmpty();

        if (!hasSuffix1 && hasSuffix2)
            return 1;
        if (hasSuffix1 && !hasSuffix2)
            return -1;
        if (hasSuffix1 && hasSuffix2)
            return split1[1].compareToIgnoreCase(split2[1]);

        return 0;
    }

    public static String extractCustomerChangelog(String rawBody) {
        if (rawBody == null || rawBody.trim().isEmpty())
            return "";
        return rawBody.trim();
    }
}
