package com.billing.simple.billsoft.dataprotection;

import com.billing.simple.billsoft.licensing.DataProtectionEntitlement;
import com.billing.simple.billsoft.licensing.LicensingConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decoupled background service for off-device Data Protection.
 * Strictly adheres to low-frequency rules:
 * - Does NOT depend on AutoBackupService or database repositories.
 * - Reads local autobackup_latest.json file on disk.
 * - Minimum necessary remote calls (upload ~once per week when >= 7 days elapsed).
 * - Failure in Data Protection NEVER impacts core application operations.
 */
@Service
public class DataProtectionService {

    private static final Logger log = LoggerFactory.getLogger(DataProtectionService.class);
    private static final String LATEST_BACKUP_NAME = "autobackup_latest.json";
    private static final String STATUS_FILE_NAME = "data_protection_status.json";
    private static final long WEEKLY_INTERVAL_DAYS = 7;

    private final DataProtectionEntitlement entitlement;
    private final BackupStorageProvider storageProvider;
    private final DataProtectionCrypto crypto;
    private final File backupDir;
    private final File statusFile;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean uploadInProgress = new AtomicBoolean(false);

    private DataProtectionStatus currentStatus;

    @Autowired
    public DataProtectionService(DataProtectionEntitlement entitlement) {
        this(entitlement, new GitHubStorageProvider(), new DataProtectionCrypto(), resolveBackupDirectory(), resolveStatusFile());
    }

    public DataProtectionService(DataProtectionEntitlement entitlement,
                                 BackupStorageProvider storageProvider,
                                 DataProtectionCrypto crypto,
                                 File backupDir,
                                 File statusFile) {
        this.entitlement = entitlement;
        this.storageProvider = storageProvider;
        this.crypto = crypto;
        this.backupDir = backupDir;
        this.statusFile = statusFile;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);

        this.currentStatus = loadStatus();

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rupeecrm-dataprotection-daemon");
            t.setDaemon(true);
            return t;
        });

        // Run background check every 60 minutes with 30-second initial startup delay
        this.scheduler.scheduleWithFixedDelay(this::performScheduledBackupCheck, 30, 3600, TimeUnit.SECONDS);
    }

    private static File resolveBackupDirectory() {
        File dataDir = com.billing.simple.billsoft.util.DataDirectoryResolver.resolveDataDirectory();
        File dir = new File(dataDir, "backups");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static File resolveStatusFile() {
        File dir = LicensingConfig.getStorageDirectory();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, STATUS_FILE_NAME);
    }

    public synchronized DataProtectionStatus loadStatus() {
        if (!statusFile.exists() || !statusFile.isFile()) {
            return new DataProtectionStatus();
        }
        try {
            return mapper.readValue(statusFile, DataProtectionStatus.class);
        } catch (Exception e) {
            return new DataProtectionStatus();
        }
    }

    public synchronized void saveStatus(DataProtectionStatus status) {
        this.currentStatus = status;
        try {
            mapper.writeValue(statusFile, status);
        } catch (Exception ignored) {
        }
    }

    /**
     * Periodic scheduled check (Hourly).
     * Local-only decision: Checks if DP is enabled and >= 7 days elapsed. Zero network calls if not due.
     */
    public void performScheduledBackupCheck() {
        try {
            if (entitlement == null || !entitlement.isDataProtectionActive()) {
                return; // DP is not active or license is expired/missing. 0 remote calls.
            }

            Instant lastSuccess = currentStatus.getLastSuccessfulCloudBackupAt();
            if (lastSuccess != null) {
                long daysSince = Duration.between(lastSuccess, Instant.now()).toDays();
                if (daysSince < WEEKLY_INTERVAL_DAYS) {
                    return; // Not due yet (< 7 days). 0 remote calls.
                }
            }

            // Trigger weekly backup
            executeBackupInternal(false);
        } catch (Throwable t) {
            // Absolute failure isolation: Never let DP background thread errors escape
            log.warn("Opportunistic Data Protection check skipped: {}", t.getMessage());
        }
    }

    /**
     * Manual or programmatic backup trigger.
     * @param force if true, bypasses the 7-day interval check.
     */
    public Map<String, Object> triggerBackupNow(boolean force) {
        Map<String, Object> resp = new HashMap<>();
        if (entitlement == null || !entitlement.isDataProtectionActive()) {
            resp.put("success", false);
            resp.put("message", "Data Protection is not active for the current license");
            return resp;
        }

        if (!uploadInProgress.compareAndSet(false, true)) {
            resp.put("success", false);
            resp.put("message", "A Data Protection backup is already in progress");
            return resp;
        }

        try {
            boolean success = executeBackupProcess();
            resp.put("success", success);
            resp.put("lastSuccessfulBackup", currentStatus.getLastSuccessfulCloudBackupAt());
            resp.put("sizeBytes", currentStatus.getLastBackupSizeBytes());
            resp.put("error", currentStatus.getLastError());
            return resp;
        } finally {
            uploadInProgress.set(false);
        }
    }

    private void executeBackupInternal(boolean force) {
        if (!uploadInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            executeBackupProcess();
        } finally {
            uploadInProgress.set(false);
        }
    }

    private boolean executeBackupProcess() {
        currentStatus.setLastAttemptAt(Instant.now());
        File backupFile = new File(backupDir, LATEST_BACKUP_NAME);

        if (!backupFile.exists() || !backupFile.isFile() || backupFile.length() == 0) {
            currentStatus.setLastError("Local backup file (" + LATEST_BACKUP_NAME + ") not found on disk");
            saveStatus(currentStatus);
            return false;
        }

        try {
            // 1. Read local backup
            byte[] rawJsonBytes = Files.readAllBytes(backupFile.toPath());

            // 2. Derive key from License ID
            String licenseId = entitlement.getLicenseId();
            SecretKey secretKey = crypto.deriveKeyFromLicenseId(licenseId);

            // 3. Compress & Encrypt into RCBP container
            byte[] encryptedContainer = crypto.encryptBackup(rawJsonBytes, secretKey);

            // 4. Upload to remote storage vault (minimum necessary calls)
            String machineId = entitlement.getMachineId();
            BackupStorageProvider.UploadResult uploadResult = storageProvider.uploadBackup(machineId, encryptedContainer, currentStatus.getLastBlobSha());

            if (uploadResult.isSuccess()) {
                currentStatus.setLastSuccessfulCloudBackupAt(Instant.now());
                currentStatus.setLastBackupSizeBytes(encryptedContainer.length);
                currentStatus.setLastBlobSha(uploadResult.getSha());
                currentStatus.setLastError(null);
                saveStatus(currentStatus);
                log.info("Data Protection cloud backup completed successfully ({} bytes)", encryptedContainer.length);
                return true;
            } else {
                currentStatus.setLastError(uploadResult.getErrorMessage());
                saveStatus(currentStatus);
                log.warn("Data Protection upload failed: {}", uploadResult.getErrorMessage());
                return false;
            }
        } catch (Exception e) {
            currentStatus.setLastError("Encryption/packaging error: " + e.getMessage());
            saveStatus(currentStatus);
            log.warn("Data Protection failed with exception: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Downloads and decrypts off-device backup for cloud restore.
     */
    public byte[] downloadAndDecryptBackup(String machineId, String licenseId) throws Exception {
        if (machineId == null || machineId.isBlank() || licenseId == null || licenseId.isBlank()) {
            throw new IllegalArgumentException("Machine ID and License ID are required for cloud restore");
        }
        byte[] encryptedBytes = storageProvider.downloadBackup(machineId);
        if (encryptedBytes == null || encryptedBytes.length == 0) {
            throw new IllegalStateException("No remote backup found for machine: " + machineId);
        }
        SecretKey secretKey = crypto.deriveKeyFromLicenseId(licenseId);
        return crypto.decryptBackup(encryptedBytes, secretKey);
    }

    public Map<String, Object> getStatusMap() {
        Map<String, Object> map = new HashMap<>();
        boolean active = entitlement != null && entitlement.isDataProtectionActive();
        boolean enabled = entitlement != null && entitlement.isDataProtectionEnabled();
        Instant expiresAt = entitlement != null ? entitlement.getDataProtectionExpiresAt() : null;

        map.put("enabled", enabled);
        map.put("active", active);
        map.put("expiresAt", expiresAt);
        map.put("lastSuccessfulCloudBackupAt", currentStatus.getLastSuccessfulCloudBackupAt());
        map.put("lastBackupSizeBytes", currentStatus.getLastBackupSizeBytes());
        map.put("lastAttemptAt", currentStatus.getLastAttemptAt());
        map.put("lastError", currentStatus.getLastError());
        map.put("inProgress", uploadInProgress.get());

        if (currentStatus.getLastSuccessfulCloudBackupAt() != null) {
            Instant nextScheduled = currentStatus.getLastSuccessfulCloudBackupAt().plus(Duration.ofDays(WEEKLY_INTERVAL_DAYS));
            map.put("nextScheduledBackupAt", nextScheduled);
        } else {
            map.put("nextScheduledBackupAt", null);
        }

        return map;
    }

    public DataProtectionStatus getCurrentStatus() {
        return currentStatus;
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
