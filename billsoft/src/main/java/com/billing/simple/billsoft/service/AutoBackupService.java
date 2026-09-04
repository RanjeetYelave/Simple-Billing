package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
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
    private static final String LATEST_BACKUP_NAME = "autobackup_latest.json";

    private final BackupService backupService;
    private final ObjectMapper objectMapper;
    private final Map<String, Object> lastBackupStatus = new HashMap<>();

    public AutoBackupService(BackupService backupService) {
        this.backupService = backupService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Resolves the protected persistent backup directory alongside the database.
     * This directory is outside the software codebase and survives software deletion/updates.
     */
    public File getBackupDirectory() {
        String dataDirPath = System.getProperty("BILLSOFT_DATA_DIR");
        if (dataDirPath == null || dataDirPath.trim().isEmpty()) {
            dataDirPath = System.getenv("BILLSOFT_DATA_DIR");
        }
        if (dataDirPath == null || dataDirPath.trim().isEmpty()) {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                String appData = System.getenv("APPDATA");
                if (appData != null && !appData.isEmpty()) {
                    dataDirPath = appData + File.separator + "SimpleBilling";
                } else {
                    dataDirPath = System.getProperty("user.home") + File.separator + ".simplebilling";
                }
            } else if (os.contains("mac")) {
                dataDirPath = System.getProperty("user.home") + "/Library/Application Support/SimpleBilling";
            } else {
                dataDirPath = System.getProperty("user.home") + File.separator + ".simplebilling";
            }
        }

        File backupDir = new File(dataDirPath, "backups");
        if (!backupDir.exists()) {
            boolean created = backupDir.mkdirs();
            if (created) {
                log.info("Created auto-backup directory at: {}", backupDir.getAbsolutePath());
            }
        }
        return backupDir;
    }

    @PostConstruct
    public void init() {
        // Load initial status from disk if previous backup exists
        File backupDir = getBackupDirectory();
        File latest = new File(backupDir, LATEST_BACKUP_NAME);
        if (latest.exists() && latest.isFile()) {
            lastBackupStatus.put("status", "READY");
            lastBackupStatus.put("lastBackupTime", latest.lastModified());
            lastBackupStatus.put("lastBackupFormatted", formatTimestamp(latest.lastModified()));
            lastBackupStatus.put("fileSizeBytes", latest.length());
            lastBackupStatus.put("filePath", latest.getAbsolutePath());
            lastBackupStatus.put("backupDir", backupDir.getAbsolutePath());
        } else {
            lastBackupStatus.put("status", "INITIALIZING");
            lastBackupStatus.put("backupDir", backupDir.getAbsolutePath());
        }

        // Check if startup auto-backup is needed (if no backup or >24h since last)
        new Thread(() -> {
            try {
                Thread.sleep(3000); // Allow server startup to complete
                checkAndRunStartupAutoBackup();
            } catch (Exception e) {
                log.warn("Startup auto-backup check encountered a non-critical error: {}", e.getMessage());
            }
        }, "AutoBackup-StartupCheck").start();
    }

    public synchronized void checkAndRunStartupAutoBackup() {
        File backupDir = getBackupDirectory();
        File latest = new File(backupDir, LATEST_BACKUP_NAME);
        long now = System.currentTimeMillis();
        long twentyFourHours = 24L * 60 * 60 * 1000;

        if (!latest.exists() || (now - latest.lastModified() > twentyFourHours)) {
            log.info("Running automatic backup on startup (last backup was >24h ago or non-existent)...");
            runAutoBackup();
        }
    }

    /**
     * Executes daily automated backup (runs daily at 2:00 AM).
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledDailyBackup() {
        log.info("Triggering scheduled daily auto-backup...");
        runAutoBackup();
    }

    /**
     * Executes a complete system auto-backup with atomic write and old backup deletion.
     */
    public synchronized Map<String, Object> runAutoBackup() {
        File backupDir = getBackupDirectory();
        long startTime = System.currentTimeMillis();
        File tempFile = new File(backupDir, "autobackup_temp_" + startTime + ".json");
        File targetFile = new File(backupDir, LATEST_BACKUP_NAME);

        try {
            log.info("Starting complete multi-firm auto-backup generation...");
            BackupDTO fullBackup = backupService.exportAllData();
            fullBackup.getMetadata().put("backupType", "DAILY_AUTO_BACKUP");
            fullBackup.getMetadata().put("generatedAt", LocalDateTime.now().toString());

            // 1. Write to temporary file first (atomic safety)
            objectMapper.writeValue(tempFile, fullBackup);

            if (!tempFile.exists() || tempFile.length() == 0) {
                throw new RuntimeException("Generated auto-backup temp file is empty or missing.");
            }

            // 2. Atomically promote/rename temp file to latest backup
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            // 3. Prune old/stray temporary backup files in directory
            cleanStrayBackupFiles(backupDir, targetFile);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Auto-backup successfully completed in {}ms. File size: {} bytes at {}",
                    elapsed, targetFile.length(), targetFile.getAbsolutePath());

            lastBackupStatus.put("status", "SUCCESS");
            lastBackupStatus.put("lastBackupTime", targetFile.lastModified());
            lastBackupStatus.put("lastBackupFormatted", formatTimestamp(targetFile.lastModified()));
            lastBackupStatus.put("fileSizeBytes", targetFile.length());
            lastBackupStatus.put("filePath", targetFile.getAbsolutePath());
            lastBackupStatus.put("backupDir", backupDir.getAbsolutePath());
            lastBackupStatus.put("durationMs", elapsed);
            lastBackupStatus.put("message", "Auto-backup verified and saved successfully.");

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
        }
    }

    /**
     * Deletes older temporary files and previous backups so only the fresh backup is retained.
     */
    private void cleanStrayBackupFiles(File backupDir, File activeBackupFile) {
        try {
            File[] files = backupDir.listFiles((dir, name) -> name.startsWith("autobackup_") && !name.equals(activeBackupFile.getName()));
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.delete()) {
                        log.debug("Pruned older backup file: {}", file.getName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Non-critical issue while cleaning older backups: {}", e.getMessage());
        }
    }

    public synchronized Map<String, Object> getStatus() {
        File backupDir = getBackupDirectory();
        File latest = new File(backupDir, LATEST_BACKUP_NAME);

        Map<String, Object> status = new HashMap<>(lastBackupStatus);
        status.put("backupDir", backupDir.getAbsolutePath());
        status.put("fileExists", latest.exists() && latest.isFile());
        if (latest.exists()) {
            status.put("fileSizeBytes", latest.length());
            status.put("lastModified", latest.lastModified());
            status.put("lastModifiedFormatted", formatTimestamp(latest.lastModified()));
        }
        status.put("scheduleCron", "Daily at 02:00 AM (0 0 2 * * *)");
        status.put("retentionPolicy", "Keep latest verified backup (Old pruned automatically)");
        return status;
    }

    private String formatTimestamp(long timeMs) {
        return DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(timeMs));
    }
}
