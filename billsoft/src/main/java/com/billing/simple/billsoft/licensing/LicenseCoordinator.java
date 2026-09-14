package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.licensing.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Orchestrates local offline startup validation and background opportunistic daily sync with GitHub.
 */
public class LicenseCoordinator {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");

    private final MachineIdentity machineIdentity;
    private final LicenseVerifier licenseVerifier;
    private final LicenseStorage licenseStorage;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;

    private LicensePayload activeLicense;
    private ValidationResult currentValidationResult = ValidationResult.CORRUPT_PAYLOAD;
    private Consumer<ValidationResult> statusChangeListener;
    private boolean dailySyncScheduled = false;

    public LicenseCoordinator() {
        this(new MachineIdentity(), new LicenseVerifier(), new LicenseStorage());
    }

    public LicenseCoordinator(MachineIdentity machineIdentity, LicenseVerifier licenseVerifier, LicenseStorage licenseStorage) {
        this.machineIdentity = machineIdentity;
        this.licenseVerifier = licenseVerifier;
        this.licenseStorage = licenseStorage;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rupeecrm-license-sync");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Fast-path local startup check (Zero network latency).
     * Returns true if application is permitted to start.
     */
    public synchronized boolean validateOnStartup() {
        String machineId = machineIdentity.getMachineId();
        this.activeLicense = licenseStorage.loadLocalLicense();

        if (activeLicense == null) {
            currentValidationResult = ValidationResult.CORRUPT_PAYLOAD;
            return false;
        }

        // 1. Verify cryptographic signature & machine identity
        ValidationResult result = licenseVerifier.verifyLicense(activeLicense, machineId);
        if (!result.isValid()) {
            currentValidationResult = result;
            return false;
        }

        // 2. Rollback check against highest recorded revision
        int highestSeen = licenseStorage.getHighestRevision();
        if (activeLicense.getRevision() < highestSeen) {
            currentValidationResult = ValidationResult.INVALID_SIGNATURE;
            return false;
        }

        // 3. Offline expiry tolerance: Check if expired
        // In offline mode, an expired license STILL ALLOWS startup (Fail-Open), but UI can show warning
        currentValidationResult = ValidationResult.VALID;

        // Schedule background sync
        scheduleDailySync();
        return true;
    }

    /**
     * Schedules the opportunistic background daily check.
     */
    public synchronized void scheduleDailySync() {
        if (dailySyncScheduled) {
            return;
        }
        dailySyncScheduled = true;
        scheduler.scheduleWithFixedDelay(this::performOpportunisticDailySync, 10, 3600, TimeUnit.SECONDS);
    }

    /**
     * Performs opportunistic background synchronization when network is available.
     */
    public void performOpportunisticDailySync() {
        syncWithRegistry(false);
    }

    /**
     * Synchronizes local license state and inbox messages with the remote registry.
     * @param force if true, bypasses the once-per-day check restriction.
     */
    public void syncWithRegistry(boolean force) {
        String today = LocalDate.now(DEFAULT_ZONE).toString();
        String lastChecked = licenseStorage.getLastSuccessfulCheckDate();

        if (!force && today.equals(lastChecked)) {
            return; // Already successfully verified today
        }

        String machineId = machineIdentity.getMachineId();

        // 1. Attempt to fetch remote license
        try {
            String body = fetchRegistryFile("licenses/" + machineId + ".lic");

            if (body != null && !body.isBlank()) {
                LicensePayload remoteLicense = mapper.readValue(body, LicensePayload.class);
                ValidationResult remoteVerify = licenseVerifier.verifyLicense(remoteLicense, machineId);

                if (remoteVerify.isValid() || remoteVerify == ValidationResult.SUSPENDED || remoteVerify == ValidationResult.REVOKED) {
                    int localHighest = licenseStorage.getHighestRevision();

                    // Reject rollback attempts
                    if (remoteLicense.getRevision() >= localHighest) {
                        // Mark today as successfully checked!
                        licenseStorage.updateSyncState(today, remoteLicense.getRevision());

                        // Update local license if remote is newer
                        if (activeLicense == null || remoteLicense.getRevision() > activeLicense.getRevision()) {
                            licenseStorage.saveLocalLicense(remoteLicense);
                            this.activeLicense = remoteLicense;
                        }

                        // Evaluate remote status
                        if (remoteLicense.getStatus() == LicenseStatus.SUSPENDED) {
                            notifyStatusChange(ValidationResult.SUSPENDED);
                            return;
                        }
                        if (remoteLicense.getStatus() == LicenseStatus.REVOKED) {
                            notifyStatusChange(ValidationResult.REVOKED);
                            return;
                        }

                        // Evaluate authenticated expiry
                        if (licenseVerifier.isExpired(remoteLicense, Instant.now())) {
                            notifyStatusChange(ValidationResult.EXPIRED);
                            return;
                        }

                        notifyStatusChange(ValidationResult.VALID);
                    }
                }
            }
        } catch (Exception e) {
            // Fail-open: Network / 404 / timeout / DNS failure does not interrupt active application
        }

        // 2. Fetch customer inbox messages
        try {
            String msgBody = fetchRegistryFile("messages/" + machineId + ".json");
            if (msgBody != null && !msgBody.isBlank()) {
                CustomerMessageEnvelope env = mapper.readValue(msgBody, CustomerMessageEnvelope.class);
                if (env.getMessages() != null) {
                    List<CustomerMessage> existingLocal = licenseStorage.loadInboxMessages();
                    java.util.Set<String> readMessageIds = new java.util.HashSet<>();
                    if (existingLocal != null) {
                        for (CustomerMessage existing : existingLocal) {
                            if (existing.isRead() && existing.getMessageId() != null) {
                                readMessageIds.add(existing.getMessageId().trim());
                            }
                        }
                    }
                    List<CustomerMessage> validMsgs = new ArrayList<>();
                    for (CustomerMessage msg : env.getMessages()) {
                        if (licenseVerifier.verifyMessage(machineId, msg)) {
                            if (msg.getMessageId() != null && readMessageIds.contains(msg.getMessageId().trim())) {
                                msg.setRead(true);
                            }
                            validMsgs.add(msg);
                        }
                    }
                    licenseStorage.saveInboxMessages(machineId, validMsgs);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Fetches file content from the remote registry using dual strategy:
     * 1. GitHub REST API contents endpoint with raw header (fast, resilient against ISP routing blocks).
     * 2. Static raw.githubusercontent.com fallback.
     */
    private String fetchRegistryFile(String subPath) {
        // Strategy 1: GitHub API
        String apiBase = System.getProperty("rupeecrm.licensing.api.url", LicensingConfig.DEFAULT_API_BASE_URL);
        String result = fetchHttpText(apiBase + "/" + subPath);
        if (result != null && !result.isBlank()) {
            return result;
        }

        // Strategy 2: Raw fallback
        String rawBase = LicensingConfig.getRegistryBaseUrl();
        return fetchHttpText(rawBase + "/" + subPath);
    }

    private String fetchHttpText(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(urlStr).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "RupeeCRM-Desktop/1.0");
            conn.setRequestProperty("Accept", "application/vnd.github.v3.raw, application/json, text/plain, */*");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code == 200) {
                try (InputStream in = conn.getInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    /**
     * Installs a newly received .lic file (e.g. from manual drop/paste or initial activation).
     */
    public synchronized ValidationResult activateLicense(LicensePayload license) {
        String machineId = machineIdentity.getMachineId();
        ValidationResult result = licenseVerifier.verifyLicense(license, machineId);
        if (!result.isValid()) {
            return result;
        }
        try {
            licenseStorage.saveLocalLicense(license);
            licenseStorage.updateSyncState(LocalDate.now(DEFAULT_ZONE).toString(), license.getRevision());
            this.activeLicense = license;
            this.currentValidationResult = ValidationResult.VALID;
            scheduleDailySync();
            notifyStatusChange(ValidationResult.VALID);
            return ValidationResult.VALID;
        } catch (Exception e) {
            return ValidationResult.CORRUPT_PAYLOAD;
        }
    }

    public void setStatusChangeListener(Consumer<ValidationResult> listener) {
        this.statusChangeListener = listener;
    }

    private void notifyStatusChange(ValidationResult result) {
        this.currentValidationResult = result;
        if (statusChangeListener != null) {
            statusChangeListener.accept(result);
        }
    }

    public LicensePayload getActiveLicense() {
        return activeLicense;
    }

    public ValidationResult getCurrentValidationResult() {
        return currentValidationResult;
    }

    public MachineIdentity getMachineIdentity() {
        return machineIdentity;
    }

    public LicenseStorage getLicenseStorage() {
        return licenseStorage;
    }

    public LicenseVerifier getLicenseVerifier() {
        return licenseVerifier;
    }
}
