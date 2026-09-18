package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.dataprotection.DataProtectionCredentialStore;
import com.billing.simple.billsoft.licensing.model.*;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.stereotype.Service;

/**
 * Orchestrates local offline startup validation and background opportunistic daily sync with GitHub.
 */
@Service
public class LicenseCoordinator implements DataProtectionEntitlement {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final int VALID_LICENSE_SYNC_INTERVAL_DAYS = 14;

    private final MachineIdentity machineIdentity;
    private final LicenseVerifier licenseVerifier;
    private final LicenseStorage licenseStorage;
    private final SnoozeManager snoozeManager;
    private final DataProtectionCredentialStore credentialStore;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.billing.simple.billsoft.service.NotificationService notificationService;

    private LicensePayload activeLicense;
    private ValidationResult currentValidationResult = ValidationResult.CORRUPT_PAYLOAD;
    private Consumer<ValidationResult> statusChangeListener;
    private volatile Runnable backupCheckHook;
    private boolean dailySyncScheduled = false;

    public LicenseCoordinator() {
        this(new MachineIdentity(), new LicenseVerifier(), new LicenseStorage(), new SnoozeManager(), new DataProtectionCredentialStore());
    }

    public LicenseCoordinator(MachineIdentity machineIdentity, LicenseVerifier licenseVerifier, LicenseStorage licenseStorage) {
        this(machineIdentity, licenseVerifier, licenseStorage, new SnoozeManager(), new DataProtectionCredentialStore());
    }

    public LicenseCoordinator(MachineIdentity machineIdentity, LicenseVerifier licenseVerifier, LicenseStorage licenseStorage, SnoozeManager snoozeManager) {
        this(machineIdentity, licenseVerifier, licenseStorage, snoozeManager, new DataProtectionCredentialStore());
    }

    public LicenseCoordinator(MachineIdentity machineIdentity, LicenseVerifier licenseVerifier, LicenseStorage licenseStorage, SnoozeManager snoozeManager, DataProtectionCredentialStore credentialStore) {
        this.machineIdentity = machineIdentity;
        this.licenseVerifier = licenseVerifier;
        this.licenseStorage = licenseStorage;
        this.snoozeManager = snoozeManager;
        this.credentialStore = credentialStore != null ? credentialStore : new DataProtectionCredentialStore();
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rupeecrm-license-sync");
            t.setDaemon(true);
            return t;
        });
    }

    public void setNotificationService(com.billing.simple.billsoft.service.NotificationService notificationService) {
        this.notificationService = notificationService;
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

        // 2. Rollback check against highest recorded revision for THIS license ID
        int highestSeen = licenseStorage.getHighestRevision();
        String recordedId = licenseStorage.getRecordedLicenseId();
        boolean sameLicense = (recordedId == null || recordedId.equals(activeLicense.getLicenseId()));
        if (sameLicense && activeLicense.getRevision() < highestSeen) {
            currentValidationResult = ValidationResult.INVALID_SIGNATURE;
            return false;
        }

        // 3. Offline expiry tolerance: Check if expired
        // In offline mode, an expired license STILL ALLOWS startup (Fail-Open), but UI can show warning
        currentValidationResult = ValidationResult.VALID;

        // Check and reset snoozes if revision changed
        snoozeManager.checkAndResetOnRevisionBump(activeLicense.getRevision());

        // Schedule background sync
        scheduleDailySync();
        return true;
    }

    /**
     * Schedules the opportunistic background check.
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
     * Low-frequency policy:
     * - If license is comfortably valid: checks at most once every 14 days.
     * - If license is expired: checks on startup/daily sync to detect operator renewals.
     * @param force if true, bypasses cadence restriction.
     */
    public void syncWithRegistry(boolean force) {
        LocalDate todayDate = LocalDate.now(DEFAULT_ZONE);
        String today = todayDate.toString();
        String lastChecked = licenseStorage.getLastSuccessfulCheckDate();

        if (!force && lastChecked != null && !lastChecked.isBlank()) {
            try {
                LocalDate lastCheckedDate = LocalDate.parse(lastChecked);
                boolean expired = licenseVerifier.isExpired(activeLicense, Instant.now());
                if (!expired) {
                    long daysSinceCheck = java.time.temporal.ChronoUnit.DAYS.between(lastCheckedDate, todayDate);
                    if (daysSinceCheck < VALID_LICENSE_SYNC_INTERVAL_DAYS) {
                        return; // Valid license checked < 14 days ago. Skip remote call.
                    }
                } else {
                    if (today.equals(lastChecked)) {
                        return; // Expired license already checked today.
                    }
                }
            } catch (Exception ignored) {
            }
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
                    String localLicId = licenseStorage.getRecordedLicenseId();
                    if (localLicId == null && activeLicense != null) {
                        localLicId = activeLicense.getLicenseId();
                    }
                    boolean isNewLicenseId = (localLicId == null || !localLicId.equals(remoteLicense.getLicenseId()));

                    // Reject rollback attempts for the same license ID
                    if (isNewLicenseId || remoteLicense.getRevision() >= localHighest) {
                        // Mark today as successfully checked!
                        licenseStorage.updateSyncState(today, remoteLicense.getRevision(), remoteLicense.getLicenseId());

                        // Update local license if remote is newer or newly issued license
                        if (activeLicense == null || isNewLicenseId || remoteLicense.getRevision() > activeLicense.getRevision()) {
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

                            if (notificationService != null && msg.getMessageId() != null && !msg.getMessageId().isBlank()) {
                                try {
                                    notificationService.createOrUpdate(com.billing.simple.billsoft.dto.NotificationRequest.builder()
                                            .firmId(com.billing.simple.billsoft.entities.Notification.GLOBAL_FIRM_ID)
                                            .eventKey("management:broadcast:" + machineId + ":" + msg.getMessageId().trim())
                                            .category(com.billing.simple.billsoft.entities.NotificationCategory.LICENSING)
                                            .priority(com.billing.simple.billsoft.entities.NotificationPriority.HIGH)
                                            .title(msg.getTitle() != null ? msg.getTitle() : "Announcement")
                                            .body(msg.getBody())
                                            .sender("RupeeCRM Management")
                                            .primaryActionLabel("Open System")
                                            .primaryActionType(com.billing.simple.billsoft.entities.NotificationActionType.NAVIGATE)
                                            .primaryActionTarget("settings")
                                            .build());
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                    licenseStorage.saveInboxMessages(machineId, validMsgs);
                }
            }
        } catch (Exception ignored) {
        }

        // 3. Fetch versioned Data Protection configuration
        try {
            String vaultConfigBody = fetchRegistryFile("config/vault-config.json");
            if (vaultConfigBody != null && !vaultConfigBody.isBlank()) {
                JsonNode root = mapper.readTree(vaultConfigBody);
                if (root.has("version") && root.has("protectedPayload") && root.has("signature")) {
                    int ver = root.get("version").asInt();
                    String payload = root.get("protectedPayload").asText();
                    String sig = root.get("signature").asText();
                    credentialStore.updateCredential(ver, payload, sig, licenseVerifier.getMasterPublicKey());
                }
            }
        } catch (Exception ignored) {
        }

        // 4. Opportunistic Data Protection backup check (Single scheduled cycle)
        if (backupCheckHook != null) {
            try {
                backupCheckHook.run();
            } catch (Throwable ignored) {
            }
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
            licenseStorage.updateSyncState(LocalDate.now(DEFAULT_ZONE).toString(), license.getRevision(), license.getLicenseId());
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

    // --- DataProtectionEntitlement Implementation ---

    @Override
    public boolean isDataProtectionEnabled() {
        return activeLicense != null && Boolean.TRUE.equals(activeLicense.getDataProtectionEnabled());
    }

    @Override
    public Instant getDataProtectionExpiresAt() {
        return activeLicense != null ? activeLicense.getDataProtectionExpiresAt() : null;
    }

    @Override
    public boolean isDataProtectionActive() {
        return licenseVerifier.isDataProtectionActive(activeLicense, Instant.now());
    }

    @Override
    public String getLicenseId() {
        return activeLicense != null && activeLicense.getLicenseId() != null ? activeLicense.getLicenseId() : "";
    }

    @Override
    public String getMachineId() {
        return machineIdentity.getMachineId();
    }

    @Override
    public void registerBackupCheckHook(Runnable hook) {
        this.backupCheckHook = hook;
    }

    public DataProtectionCredentialStore getCredentialStore() {
        return credentialStore;
    }

    // --- Expiry Days Calculation Helpers ---

    public Long getLicenseDaysRemaining() {
        if (activeLicense == null || activeLicense.getExpiresAt() == null || activeLicense.getPlan() == MembershipPlan.GOLD) {
            return null; // Lifetime / no expiry
        }
        Instant now = Instant.now();
        if (now.isAfter(activeLicense.getExpiresAt())) {
            return 0L;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(now, activeLicense.getExpiresAt());
    }

    public Long getDataProtectionDaysRemaining() {
        if (activeLicense == null || !Boolean.TRUE.equals(activeLicense.getDataProtectionEnabled()) || activeLicense.getDataProtectionExpiresAt() == null) {
            return null; // Lifetime / disabled
        }
        Instant now = Instant.now();
        if (now.isAfter(activeLicense.getDataProtectionExpiresAt())) {
            return 0L;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(now, activeLicense.getDataProtectionExpiresAt());
    }

    public SnoozeManager getSnoozeManager() {
        return snoozeManager;
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
