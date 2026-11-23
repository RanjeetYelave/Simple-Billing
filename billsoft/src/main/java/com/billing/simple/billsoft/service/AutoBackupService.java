package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AutoBackupService {

    private static final Logger log = LoggerFactory.getLogger(AutoBackupService.class);
    public static final String LATEST_BACKUP_NAME = "autobackup_latest.json";
    private static final long MIN_DISK_SPACE_BYTES = 50L * 1024L * 1024L; // 50 MB

    private final BackupService backupService;
    private final BackupValidationService backupValidationService;
    private final BackupRecoveryCoordinator coordinator;
    private final ObjectMapper objectMapper;
    private final Map<String, Object> lastBackupStatus = new HashMap<>();

    @Value("${app.autobackup.startup-check.enabled:true}")
    private boolean startupCheckEnabled = true;

    @Value("${app.autobackup.retention-count:7}")
    private int retentionCount = 7;

    @Autowired
    public AutoBackupService(BackupService backupService,
                             BackupValidationService backupValidationService,
                             @Autowired(required = false) BackupRecoveryCoordinator coordinator) {
        this.backupService = backupService;
        this.backupValidationService = backupValidationService;
        this.coordinator = coordinator != null ? coordinator : new BackupRecoveryCoordinator();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public AutoBackupService(BackupService backupService, BackupValidationService backupValidationService) {
        this(backupService, backupValidationService, new BackupRecoveryCoordinator());
    }

    public int getRetentionCount() {
        return retentionCount;
    }

    public void setRetentionCount(int retentionCount) {
        this.retentionCount = Math.max(1, retentionCount);
    }

    /**
     * Resolves the protected persistent backup directory alongside the database.
     * This directory is outside the software codebase and survives software deletion/updates.
     */
    public File getBackupDirectory() {
        return com.billing.simple.billsoft.util.DataDirectoryResolver.resolveBackupsDirectory();
    }

    @PostConstruct
    public void init() {
        // Load initial status from disk if previous backup exists
        File backupDir = getBackupDirectory();
        File latest = new File(backupDir, LATEST_BACKUP_NAME);
        if (latest.exists() && latest.isFile() && latest.length() > 0) {
            boolean today = isTodayBackupPresent();
            lastBackupStatus.put("status", today ? "HEALTHY" : "READY");
            lastBackupStatus.put("lastBackupTime", latest.lastModified());
            lastBackupStatus.put("lastBackupFormatted", formatTimestamp(latest.lastModified()));
            lastBackupStatus.put("fileSizeBytes", latest.length());
            lastBackupStatus.put("filePath", latest.getAbsolutePath());
            lastBackupStatus.put("backupDir", backupDir.getAbsolutePath());
            lastBackupStatus.put("isTodayBackup", today);
        } else {
            lastBackupStatus.put("status", "INITIALIZING");
            lastBackupStatus.put("backupDir", backupDir.getAbsolutePath());
            lastBackupStatus.put("isTodayBackup", false);
        }

        // Check if startup auto-backup is needed for today
        if (startupCheckEnabled) {
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // Allow server startup to complete
                    ensureTodayBackup();
                } catch (Exception e) {
                    log.warn("Startup auto-backup check encountered a non-critical error: {}", e.getMessage());
                }
            }, "AutoBackup-StartupCheck").start();
        }
    }

    /**
     * Daily scheduled auto-backup cron job (default: 2:00 AM every day).
     */
    @Scheduled(cron = "${app.autobackup.cron:0 0 2 * * ?}")
    public void scheduledDailyBackup() {
        log.info("Scheduled daily auto-backup triggered by cron.");
        runAutoBackup();
    }

    /**
     * Periodic missed-schedule recovery check (runs hourly).
     * If the machine was off or asleep at 2:00 AM, this ensures today's backup is generated.
     */
    @Scheduled(fixedRateString = "${app.autobackup.check-interval-ms:3600000}", initialDelay = 15000)
    public void periodicMissedScheduleCheck() {
        if (!isTodayBackupPresent()) {
            log.info("Periodic auto-backup check: No verified backup found for today ({}). Executing missed auto-backup...",
                    java.time.LocalDate.now());
            runAutoBackup();
        }
    }

    /**
     * Checks if a valid, non-empty automated backup file exists and was created today.
     */
    public boolean isTodayBackupPresent() {
        File backupDir = getBackupDirectory();
        File latest = new File(backupDir, LATEST_BACKUP_NAME);
        if (!latest.exists() || !latest.isFile() || latest.length() == 0) {
            return false;
        }
        java.time.LocalDate backupDate = Instant.ofEpochMilli(latest.lastModified())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        return backupDate.equals(java.time.LocalDate.now());
    }

    /**
     * Ensures an automated backup dated today exists. If not, generates one immediately.
     */
    public synchronized Map<String, Object> ensureTodayBackup() {
        if (isTodayBackupPresent()) {
            log.debug("Automated daily backup for today is already present and verified.");
            return getStatus();
        }
        log.info("No verified backup found for today ({}). Generating auto-backup now...", java.time.LocalDate.now());
        return runAutoBackup();
    }

    public synchronized void checkAndRunStartupAutoBackup() {
        ensureTodayBackup();
    }

    /**
     * Executes a complete system auto-backup with disk space validation, atomic write,
     * read-back integrity verification, historical archiving, and retention management.
     */
    public synchronized Map<String, Object> runAutoBackup() {
        if (com.billing.simple.billsoft.demo.EnvironmentModeContext.isDemoMode()) {
            log.info("Automated backup skipped: RupeeCRM is currently in DEMO mode. Demo data must never be saved to production backups.");
            lastBackupStatus.put("status", "SKIPPED_DEMO_MODE");
            lastBackupStatus.put("message", "Automated backup skipped: RupeeCRM is currently running in Demo Mode.");
            return new HashMap<>(lastBackupStatus);
        }

        if (!coordinator.tryLock()) {
            log.info("Automated backup skipped: System recovery, restore, or reset operation is currently in progress.");
            lastBackupStatus.put("status", "SKIPPED_RECOVERY_IN_PROGRESS");
            lastBackupStatus.put("message", "Automated backup skipped: System recovery, restore, or reset operation is in progress.");
            return new HashMap<>(lastBackupStatus);
        }

        File backupDir = getBackupDirectory();
        long startTime = System.currentTimeMillis();
        File tempFile = new File(backupDir, "autobackup_temp_" + startTime + ".json");
        File targetFile = new File(backupDir, LATEST_BACKUP_NAME);

        try {
            // 0. Disk Space Preflight Check
            long usableSpace = backupDir.getUsableSpace();
            if (usableSpace > 0 && usableSpace < MIN_DISK_SPACE_BYTES) {
                String errMsg = "INSUFFICIENT_DISK_SPACE: Target backup partition has only "
                        + (usableSpace / (1024 * 1024)) + " MB available (minimum 50 MB required).";
                log.error(errMsg);
                lastBackupStatus.put("status", "ERROR");
                lastBackupStatus.put("lastError", errMsg);
                lastBackupStatus.put("lastErrorTime", System.currentTimeMillis());
                return new HashMap<>(lastBackupStatus);
            }

            log.info("Starting complete multi-firm auto-backup generation...");
            BackupDTO fullBackup = backupService.exportAllData();
            fullBackup.getMetadata().put("backupType", "DAILY_AUTO_BACKUP");
            fullBackup.getMetadata().put("generatedAt", LocalDateTime.now().toString());

            // Validate in-memory structure
            backupValidationService.validate(fullBackup);

            // 1. Write to temporary file first (atomic safety)
            objectMapper.writeValue(tempFile, fullBackup);

            if (!tempFile.exists() || tempFile.length() == 0) {
                throw new IOException("Generated auto-backup temp file is empty or missing.");
            }

            // 2. Read-back verification of the written on-disk file
            BackupDTO verifiedBackup = objectMapper.readValue(tempFile, BackupDTO.class);
            backupValidationService.validate(verifiedBackup);

            byte[] tempBytes = Files.readAllBytes(tempFile.toPath());
            String sha256 = BackupArchiveService.calculateSha256(tempBytes);

            // 3. Create timestamped historical backup copy for multi-generation recovery
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File timestampedFile = new File(backupDir, "autobackup_" + timestamp + ".json");
            Files.copy(tempFile.toPath(), timestampedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // 4. Atomically promote/rename temp file to latest backup
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            // 5. Apply retention policy (prune old historical backups, but NEVER delete the only valid backup)
            applyRetentionPolicy(backupDir, targetFile);

            // 6. Prune any stray temporary backup files
            cleanStrayBackupFiles(backupDir, targetFile);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Auto-backup successfully completed in {}ms. File size: {} bytes, SHA-256: {} at {}",
                    elapsed, targetFile.length(), sha256, targetFile.getAbsolutePath());

            int firmsCount = fullBackup.getAllFirms() != null ? fullBackup.getAllFirms().size() : 1;
            int recordCount = (fullBackup.getInvoices() != null ? fullBackup.getInvoices().size() : 0)
                    + (fullBackup.getCustomers() != null ? fullBackup.getCustomers().size() : 0)
                    + (fullBackup.getProducts() != null ? fullBackup.getProducts().size() : 0)
                    + (fullBackup.getParties() != null ? fullBackup.getParties().size() : 0)
                    + (fullBackup.getNotifications() != null ? fullBackup.getNotifications().size() : 0);

            lastBackupStatus.put("status", "HEALTHY");
            lastBackupStatus.put("lastBackupTime", targetFile.lastModified());
            lastBackupStatus.put("lastBackupFormatted", formatTimestamp(targetFile.lastModified()));
            lastBackupStatus.put("fileSizeBytes", targetFile.length());
            lastBackupStatus.put("filePath", targetFile.getAbsolutePath());
            lastBackupStatus.put("backupDir", backupDir.getAbsolutePath());
            lastBackupStatus.put("checksumSha256", sha256);
            lastBackupStatus.put("durationMs", elapsed);
            lastBackupStatus.put("isTodayBackup", true);
            lastBackupStatus.put("firmsCount", firmsCount);
            lastBackupStatus.put("recordCount", recordCount);
            lastBackupStatus.put("message", "Auto-backup verified and saved successfully for today.");
            lastBackupStatus.remove("lastError");

            return new HashMap<>(lastBackupStatus);
        } catch (Exception e) {
            log.error("Failed to complete automated backup: {}", e.getMessage(), e);
            if (tempFile.exists()) {
                tempFile.delete();
            }
            lastBackupStatus.put("status", "ERROR");
            lastBackupStatus.put("lastError", e.getMessage());
            lastBackupStatus.put("lastErrorTime", System.currentTimeMillis());
            return new HashMap<>(lastBackupStatus);
        } finally {
            coordinator.unlock();
        }
    }

    /**
     * Enforces the retention policy on historical timestamped backups.
     * Prunes backups older than the retention threshold while guaranteeing
     * that the only valid backup is NEVER deleted.
     */
    public void applyRetentionPolicy(File backupDir, File activeBackupFile) {
        try {
            File[] historical = backupDir.listFiles((dir, name) ->
                    name.startsWith("autobackup_") && name.endsWith(".json")
                            && !name.equals(LATEST_BACKUP_NAME)
                            && !name.startsWith("autobackup_temp_"));

            if (historical != null && historical.length > retentionCount) {
                Arrays.sort(historical, Comparator.comparingLong(File::lastModified));
                int toDelete = historical.length - retentionCount;
                for (int i = 0; i < toDelete; i++) {
                    // Safety check: Never delete if it's the last remaining historical backup or identical to active
                    if (historical.length - i > 1 && !historical[i].getAbsolutePath().equals(activeBackupFile.getAbsolutePath())) {
                        log.info("Pruned historical auto-backup under retention policy (keep last {}): {}",
                                retentionCount, historical[i].getName());
                        historical[i].delete();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Non-critical issue applying auto-backup retention policy: {}", e.getMessage());
        }
    }

    /**
     * Deletes only temporary staging files from previous backup generation attempts.
     * Historical daily backups, manual backups, and other user files are never touched.
     */
    private void cleanStrayBackupFiles(File backupDir, File activeBackupFile) {
        try {
            File[] files = backupDir.listFiles((dir, name) -> name.startsWith("autobackup_temp_"));
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.delete()) {
                        log.debug("Pruned temporary backup file: {}", file.getName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Non-critical issue while cleaning temporary backup files: {}", e.getMessage());
        }
    }

    public synchronized Map<String, Object> getStatus() {
        File backupDir = getBackupDirectory();
        File latest = new File(backupDir, LATEST_BACKUP_NAME);
        boolean fileExists = latest.exists() && latest.isFile() && latest.length() > 0;
        boolean isToday = isTodayBackupPresent();

        Map<String, Object> status = new HashMap<>(lastBackupStatus);
        status.put("backupDir", backupDir.getAbsolutePath());
        status.put("fileExists", fileExists);
        status.put("isTodayBackup", isToday);
        status.put("status", isToday ? "HEALTHY" : (fileExists ? "READY" : "INITIALIZING"));
        if (fileExists) {
            status.put("fileSizeBytes", latest.length());
            status.put("lastModified", latest.lastModified());
            status.put("lastModifiedFormatted", formatTimestamp(latest.lastModified()));
            status.put("filePath", latest.getAbsolutePath());
        }
        status.put("strategy", "Daily Scheduled & Missed-Run Auto-Recovery");
        status.put("scheduleCron", "0 0 2 * * ? (Daily at 2:00 AM) + Hourly Missed-Schedule Sweep");
        status.put("retentionPolicy", "Keep latest verified backup + last " + retentionCount + " historical daily snapshots");
        status.put("retentionCount", retentionCount);
        return status;
    }

    private String formatTimestamp(long timeMs) {
        return DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(timeMs));
    }
}
