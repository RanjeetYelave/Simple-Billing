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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * Orchestrates local offline startup validation, low-traffic background synchronization,
 * and deliberate in-flight locked recovery for RupeeCRM Schema 3 licensing.
 */
@Service
public class LicenseCoordinator implements DataProtectionEntitlement {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final int NON_EMI_SYNC_INTERVAL_DAYS = 30;
    private static final int ACTIVE_EMI_SYNC_INTERVAL_DAYS = 3;

    private final MachineIdentity machineIdentity;
    private final LicenseVerifier licenseVerifier;
    private final LicenseStorage licenseStorage;
    private final SnoozeManager snoozeManager;
    private final DataProtectionCredentialStore credentialStore;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;

    private final AtomicBoolean inFlightSyncLock = new AtomicBoolean(false);
    private Function<String, String> registryFetcher;

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
        this.registryFetcher = this::fetchRegistryFileDefault;
    }

    public void setNotificationService(com.billing.simple.billsoft.service.NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void setRegistryFetcher(Function<String, String> customFetcher) {
        this.registryFetcher = customFetcher != null ? customFetcher : this::fetchRegistryFileDefault;
    }

    /**
     * Fast-path local startup check (Zero network latency).
     * Returns true if application is permitted to start.
     */
    public synchronized boolean validateOnStartup() {
        String machineId = machineIdentity.getMachineId();
        this.activeLicense = licenseStorage.loadLocalLicense();

        // If local license is missing or not valid, attempt initial online activation sync
        if (activeLicense == null || !licenseVerifier.verifyLicense(activeLicense, machineId).isValid()) {
            try {
                syncWithRegistry(true);
            } catch (Exception ignored) {
            }
        }

        if (activeLicense == null) {
            currentValidationResult = ValidationResult.CORRUPT_PAYLOAD;
            return false;
        }

        // 1. Verify cryptographic signature & machine identity
        ValidationResult result = licenseVerifier.verifyLicense(activeLicense, machineId);
        if (!result.isValid() && result != ValidationResult.EMI_RESTRICTED) {
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

        currentValidationResult = result;

        // Check and reset snoozes if revision changed
        snoozeManager.checkAndResetOnRevisionBump(activeLicense.getRevision());

        // Schedule background low-traffic cadence sync
        scheduleDailySync();
        return true;
    }

    /**
     * Schedules opportunistic low-traffic background synchronization.
     */
    public synchronized void scheduleDailySync() {
        if (dailySyncScheduled) {
            return;
        }
        dailySyncScheduled = true;
        scheduler.scheduleWithFixedDelay(this::performOpportunisticDailySync, 10, 3600, TimeUnit.SECONDS);
    }

    public void performOpportunisticDailySync() {
        syncWithRegistry(false);
    }

    /**
     * Synchronizes local license state with the remote registry following Low-Traffic Rules:
     * - Fully Paid / Non-EMI: checks at most once every 30 days.
     * - Active Outstanding EMI: checks at most once every 3 days.
     * - Restricted: checks once per login.
     * - Expired: checks once per login.
     * - Force ("I HAVE PAID EMI"): exactly 1 immediate check guarded by in-flight mutex.
     *
     * In-Flight Mutex: If a sync is already executing, concurrent attempts return immediately.
     */
    public boolean syncWithRegistry(boolean force) {
        if (!inFlightSyncLock.compareAndSet(false, true)) {
            // In-flight mutex locked: concurrent call rejected to prevent duplicate network traffic
            return false;
        }

        try {
            LocalDate todayDate = LocalDate.now(DEFAULT_ZONE);
            String today = todayDate.toString();
            String lastChecked = licenseStorage.getLastSuccessfulCheckDate();

            if (!force && lastChecked != null && !lastChecked.isBlank()) {
                try {
                    LocalDate lastCheckedDate = LocalDate.parse(lastChecked);
                    long daysSinceCheck = java.time.temporal.ChronoUnit.DAYS.between(lastCheckedDate, todayDate);

                    boolean hasActiveEmi = activeLicense != null && activeLicense.getEmi() != null && 
                            Boolean.TRUE.equals(activeLicense.getEmi().getEnabled()) &&
                            !"SETTLED".equalsIgnoreCase(activeLicense.getEmi().getState() != null ? activeLicense.getEmi().getState().getEmiStatus() : null);

                    boolean isRestrictedOrExpired = currentValidationResult == ValidationResult.EMI_RESTRICTED ||
                            licenseVerifier.isExpired(activeLicense, Instant.now());

                    if (isRestrictedOrExpired) {
                        if (today.equals(lastChecked)) {
                            return false; // Already checked on login today
                        }
                    } else if (hasActiveEmi) {
                        if (daysSinceCheck < ACTIVE_EMI_SYNC_INTERVAL_DAYS) {
                            return false; // Active EMI checked < 3 days ago. Skip network call.
                        }
                    } else {
                        if (daysSinceCheck < NON_EMI_SYNC_INTERVAL_DAYS) {
                            return false; // Non-EMI checked < 30 days ago. Skip network call.
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            String machineId = machineIdentity.getMachineId();

            // 1. Fetch remote license from registry
            try {
                String body = registryFetcher.apply("licenses/" + machineId + ".lic");

                if (body != null && !body.isBlank()) {
                    String cleanJson = deobfuscateIfNeeded(body.trim());
                    LicensePayload remoteLicense = mapper.readValue(cleanJson, LicensePayload.class);
                    ValidationResult remoteVerify = licenseVerifier.verifyLicense(remoteLicense, machineId);

                    if (remoteVerify.isValid() || remoteVerify == ValidationResult.EMI_RESTRICTED ||
                            remoteVerify == ValidationResult.SUSPENDED || remoteVerify == ValidationResult.REVOKED) {
                        
                        int localHighest = licenseStorage.getHighestRevision();
                        String localLicId = licenseStorage.getRecordedLicenseId();
                        if (localLicId == null && activeLicense != null) {
                            localLicId = activeLicense.getLicenseId();
                        }
                        boolean isNewLicenseId = (localLicId == null || !localLicId.equals(remoteLicense.getLicenseId()));

                        // Reject rollback attempts for the same license ID
                        if (isNewLicenseId || remoteLicense.getRevision() >= localHighest) {
                            licenseStorage.updateSyncState(today, remoteLicense.getRevision(), remoteLicense.getLicenseId());

                            if (activeLicense == null || !licenseStorage.getLicenseFile().exists() || isNewLicenseId || remoteLicense.getRevision() >= activeLicense.getRevision()) {
                                licenseStorage.saveLocalLicense(remoteLicense);
                                this.activeLicense = remoteLicense;
                            }

                            if (remoteLicense.getStatus() == LicenseStatus.SUSPENDED) {
                                notifyStatusChange(ValidationResult.SUSPENDED);
                                return true;
                            }
                            if (remoteLicense.getStatus() == LicenseStatus.REVOKED) {
                                notifyStatusChange(ValidationResult.REVOKED);
                                return true;
                            }
                            if (remoteVerify == ValidationResult.EMI_RESTRICTED) {
                                notifyStatusChange(ValidationResult.EMI_RESTRICTED);
                                return true;
                            }

                            if (licenseVerifier.isExpired(remoteLicense, Instant.now())) {
                                notifyStatusChange(ValidationResult.EXPIRED);
                                return true;
                            }

                            notifyStatusChange(ValidationResult.VALID);
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                // Fail-open network resiliency: Network/DNS/HTTP error preserves existing valid local license
            }

            // 2. Fetch customer inbox messages
            try {
                String msgBody = registryFetcher.apply("messages/" + machineId + ".json");
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
                                                .eventKey("management:broadcast:" + msg.getMessageId().trim())
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

            // 3. Fetch Data Protection configuration
            try {
                String vaultConfigBody = registryFetcher.apply("config/vault-config.json");
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

            if (backupCheckHook != null) {
                try {
                    backupCheckHook.run();
                } catch (Throwable ignored) {
                }
            }

            return true;
        } finally {
            inFlightSyncLock.set(false);
        }
    }

    private String fetchRegistryFileDefault(String subPath) {
        String rawBase = LicensingConfig.getRegistryBaseUrl();
        String result = fetchHttpText(rawBase + "/" + subPath);
        if (result != null && !result.isBlank()) {
            return result;
        }
        String apiBase = LicensingConfig.getApiBaseUrl();
        String branch = LicensingConfig.getBranch();
        return fetchHttpText(apiBase + "/" + subPath + "?ref=" + branch);
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
     * Installs a newly received .lic file.
     */
    public synchronized ValidationResult activateLicense(LicensePayload license) {
        String machineId = machineIdentity.getMachineId();
        ValidationResult result = licenseVerifier.verifyLicense(license, machineId);
        if (!result.isValid() && result != ValidationResult.EMI_RESTRICTED) {
            return result;
        }
        try {
            licenseStorage.saveLocalLicense(license);
            licenseStorage.updateSyncState(LocalDate.now(DEFAULT_ZONE).toString(), license.getRevision(), license.getLicenseId());
            this.activeLicense = license;
            this.currentValidationResult = result;
            scheduleDailySync();
            notifyStatusChange(result);
            return result;
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

    public Long getLicenseDaysRemaining() {
        if (activeLicense == null || activeLicense.getExpiresAt() == null || activeLicense.getPlan() == MembershipPlan.GOLD) {
            return null; // Lifetime
        }
        Instant now = Instant.now();
        if (now.isAfter(activeLicense.getExpiresAt())) {
            return 0L;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(now, activeLicense.getExpiresAt());
    }

    public Long getDataProtectionDaysRemaining() {
        if (activeLicense == null || !Boolean.TRUE.equals(activeLicense.getDataProtectionEnabled()) || activeLicense.getDataProtectionExpiresAt() == null) {
            return null;
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

    private String deobfuscateIfNeeded(String text) {
        if (text == null || text.isBlank()) return text;
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        try {
            byte[] binary = java.util.Base64.getDecoder().decode(trimmed);
            byte[] unmasked = new byte[binary.length];
            for (int i = 0; i < binary.length; i++) {
                unmasked[i] = (byte) (binary[i] ^ 0x5C);
            }
            String result = new String(unmasked, java.nio.charset.StandardCharsets.UTF_8);
            if (result.startsWith("{") || result.startsWith("[")) {
                return result;
            }
        } catch (Exception ignored) {
        }
        return trimmed;
    }
}
