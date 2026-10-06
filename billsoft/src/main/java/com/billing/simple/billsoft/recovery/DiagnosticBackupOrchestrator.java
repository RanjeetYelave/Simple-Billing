package com.billing.simple.billsoft.recovery;

import com.billing.simple.billsoft.licensing.DataProtectionEntitlement;
import com.billing.simple.billsoft.dataprotection.DataProtectionService;
import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.service.BackupValidationService;
import com.billing.simple.billsoft.util.DataDirectoryResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class DiagnosticBackupOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticBackupOrchestrator.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int MAX_SNAPSHOT_GENERATIONS = 14;
    private static final long MAX_SNAPSHOT_BYTES = 1024L * 1024L * 1024L; // 1 GB

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final BackupService backupService;
    private final BackupValidationService backupValidationService;
    private final FirmDetailsRepository firmDetailsRepo;
    private final DataProtectionService dataProtectionService;
    private final DataProtectionEntitlement dataProtectionEntitlement;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    // Concurrency guard: prevents simultaneous restore or snapshot collisions
    private final ReentrantLock recoveryOperationLock = new ReentrantLock();

    @Autowired
    public DiagnosticBackupOrchestrator(DataSource dataSource,
                                        PlatformTransactionManager transactionManager,
                                        BackupService backupService,
                                        BackupValidationService backupValidationService,
                                        FirmDetailsRepository firmDetailsRepo,
                                        @Autowired(required = false) DataProtectionService dataProtectionService,
                                        @Autowired(required = false) DataProtectionEntitlement dataProtectionEntitlement,
                                        ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.backupService = backupService;
        this.backupValidationService = backupValidationService;
        this.firmDetailsRepo = firmDetailsRepo;
        this.dataProtectionService = dataProtectionService;
        this.dataProtectionEntitlement = dataProtectionEntitlement;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates an H2 native binary snapshot using BACKUP TO with concurrency locking.
     */
    public Map<String, Object> createH2Snapshot() {
        if (!recoveryOperationLock.tryLock()) {
            throw new IllegalStateException("Another recovery or snapshot operation is currently in progress. Please wait for it to complete.");
        }
        try {
            File snapshotsDir = DataDirectoryResolver.resolveDiagnosticSnapshotsDirectory();
            if (!snapshotsDir.exists()) {
                snapshotsDir.mkdirs();
            }
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String filename = "h2_snapshot_" + timestamp + ".zip";
            File targetZip = new File(snapshotsDir, filename);

            log.info("Creating H2 physical snapshot: {}", targetZip.getAbsolutePath());
            executeH2Backup(targetZip);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("type", "H2_SNAPSHOT");
            result.put("filename", filename);
            result.put("path", targetZip.getAbsolutePath());
            result.put("sizeBytes", targetZip.length());
            result.put("timestamp", timestamp);
            result.put("recoveryId", "H2:" + filename);
            return result;
        } finally {
            recoveryOperationLock.unlock();
        }
    }

    /**
     * Creates a validated JSON diagnostic snapshot using atomic staging, retention cleanup, and concurrency locking.
     */
    public Map<String, Object> createJsonSnapshot() throws Exception {
        if (!recoveryOperationLock.tryLock()) {
            throw new IllegalStateException("Another recovery or snapshot operation is currently in progress. Please wait for it to complete.");
        }
        File tempStageFile = null;
        try {
            BackupDTO backup = backupService.exportAllData();
            backupValidationService.validate(backup);

            File stagingDir = DataDirectoryResolver.resolveStagingDirectory();
            File snapshotsDir = DataDirectoryResolver.resolveDiagnosticSnapshotsDirectory();
            if (!snapshotsDir.exists()) {
                snapshotsDir.mkdirs();
            }

            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String filename = "diagnostic_snapshot_" + timestamp + ".json";

            tempStageFile = new File(stagingDir, "stage_" + UUID.randomUUID() + ".tmp");
            File finalFile = new File(snapshotsDir, filename);

            // Write to staging file first
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempStageFile, backup);

            // Validate temp file
            if (!tempStageFile.exists() || tempStageFile.length() == 0) {
                throw new IllegalStateException("Snapshot staging file is empty or missing");
            }

            // Atomic move to target
            Files.move(tempStageFile.toPath(), finalFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            // Apply retention policy
            applySnapshotRetention(snapshotsDir, finalFile);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("type", "JSON_SNAPSHOT");
            result.put("filename", filename);
            result.put("path", finalFile.getAbsolutePath());
            result.put("sizeBytes", finalFile.length());
            result.put("timestamp", timestamp);
            result.put("recoveryId", "DIAGNOSTIC:" + filename);
            result.put("sha256", DiagnosticBackupScanner.computeSha256(finalFile));
            return result;
        } finally {
            if (tempStageFile != null && tempStageFile.exists()) {
                tempStageFile.delete();
            }
            recoveryOperationLock.unlock();
        }
    }

    /**
     * Executes the strict 10-step restore workflow:
     * 1. Authenticate (handled by controller filter / auth service)
     * 2. Validate recovery ID & confirmation
     * 3. Load backup into memory
     * 4. Pre-flight structural and conflict validation
     * 5. MANDATORY PRE-RESTORE SAFETY SNAPSHOT (outside transaction, aborts on failure)
     * 6. Abort immediately if safety snapshot fails
     * 7. Begin restore transaction
     * 8. Execute importSelectiveData()
     * 9. Post-restore integrity verification
     * 10. Commit only if all checks succeed (automatic via TransactionTemplate)
     */
    public Map<String, Object> restoreBackup(String recoveryId, String mode, String confirmation) throws Exception {
        return restoreBackup(recoveryId, null, mode, null, confirmation);
    }

    public Map<String, Object> restoreBackup(String recoveryId, List<Long> firmIds, String mode, Long targetFirmId, String confirmation) throws Exception {
        if (!recoveryOperationLock.tryLock()) {
            throw new IllegalStateException("Another recovery or snapshot operation is currently in progress. Please wait for it to complete.");
        }
        try {
            // 1. Strict confirmation validation
            if (!"CONFIRM".equalsIgnoreCase(confirmation != null ? confirmation.trim() : "")) {
                throw new IllegalArgumentException("Restore rejected: Explicit 'CONFIRM' string required");
            }

            // 2. Validate restore mode (default to clone, reject clean/clean_wipe)
            String effectiveMode = (mode != null && !mode.isBlank()) ? mode.trim().toLowerCase() : "clone";
            if (!"clone".equals(effectiveMode) && !"merge".equals(effectiveMode)) {
                throw new IllegalArgumentException("Unsupported restore mode: " + mode + ". Only 'clone' and 'merge' are permitted.");
            }

            // 3. Load backup DTO into memory
            BackupDTO backup;
            if ("OFFSITE".equalsIgnoreCase(recoveryId)) {
                if (dataProtectionService == null || dataProtectionEntitlement == null) {
                    throw new IllegalStateException("Data Protection service is unavailable for cloud restore");
                }
                String machineId = dataProtectionEntitlement.getMachineId();
                String licenseId = dataProtectionEntitlement.getLicenseId();
                byte[] decrypted = dataProtectionService.downloadAndDecryptBackup(machineId, licenseId);
                backup = objectMapper.readValue(decrypted, BackupDTO.class);
            } else {
                File targetFile = DiagnosticRecoveryPathValidator.resolveAndValidateFile(recoveryId);
                if (targetFile == null || !targetFile.exists() || !targetFile.isFile()) {
                    throw new IllegalArgumentException("Backup file does not exist: " + recoveryId);
                }
                if (targetFile.getName().toLowerCase().endsWith(".zip")) {
                    throw new IllegalArgumentException("H2 physical snapshot restore requires offline database file replacement. Use JSON snapshot for in-process transactional restore.");
                }
                if (targetFile.length() == 0) {
                    throw new IllegalArgumentException("Snapshot file is empty (0 bytes). Cannot restore empty snapshot.");
                }
                try {
                    backup = objectMapper.readValue(targetFile, BackupDTO.class);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Cannot restore corrupted snapshot: " + e.getMessage(), e);
                }
            }

            // 4. Pre-flight structural and schema validation
            backupValidationService.validate(backup);

            // 5. MANDATORY PRE-RESTORE SAFETY SNAPSHOT
            // Created strictly BEFORE opening the database mutation transaction.
            // If safety snapshot fails, RESTORE MUST NOT START!
            File snapshotsDir = DataDirectoryResolver.resolveDiagnosticSnapshotsDirectory();
            if (!snapshotsDir.exists()) {
                snapshotsDir.mkdirs();
            }
            String preRestoreTimestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String preRestoreFilename = "pre_restore_" + preRestoreTimestamp + ".zip";
            File preRestoreZip = new File(snapshotsDir, preRestoreFilename);

            try {
                log.info("Creating mandatory pre-restore safety snapshot: {}", preRestoreZip.getAbsolutePath());
                executeH2Backup(preRestoreZip);
                if (!preRestoreZip.exists() || preRestoreZip.length() == 0) {
                    throw new IOException("Pre-restore snapshot file was not created or is 0 bytes");
                }
            } catch (Exception e) {
                log.error("CRITICAL: Mandatory pre-restore snapshot failed. Aborting restore: {}", e.getMessage(), e);
                throw new IllegalStateException("Restore blocked: Mandatory pre-restore safety snapshot could not be created: " + e.getMessage(), e);
            }

            // 7, 8, 9, 10. Execute Transactional Restore with Post-Verification
            // TransactionTemplate ensures rollback if importSelectiveData throws OR post-verification fails
            log.info("Executing transactional restore in '{}' mode from backup '{}' (firms: {})", effectiveMode, recoveryId, firmIds);
            Set<Long> selectedFirmIds = (firmIds != null && !firmIds.isEmpty()) ? new HashSet<>(firmIds) : null;
            List<FirmDetails> restoredFirms = transactionTemplate.execute(status -> {
                List<FirmDetails> firms = backupService.importSelectiveData(backup, selectedFirmIds, effectiveMode, targetFirmId);
                if (firms == null || firms.isEmpty()) {
                    status.setRollbackOnly();
                    throw new IllegalStateException("Post-restore validation failed: No firms were restored.");
                }
                long firmCount = firmDetailsRepo.count();
                if (firmCount == 0) {
                    status.setRollbackOnly();
                    throw new IllegalStateException("Post-restore validation failed: Database contains 0 firms after restore.");
                }
                return firms;
            });

            long finalFirmCount = firmDetailsRepo.count();
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("mode", effectiveMode);
            result.put("preRestoreSnapshot", preRestoreFilename);
            result.put("restoredFirmCount", restoredFirms != null ? restoredFirms.size() : 0);
            result.put("restoredFirms", restoredFirms);
            result.put("totalFirmsInDb", finalFirmCount);
            result.put("message", "Backup restored successfully in " + effectiveMode + " mode.");
            return result;
        } finally {
            recoveryOperationLock.unlock();
        }
    }

    /**
     * Executes H2 database physical snapshot.
     * Uses native 'BACKUP TO' for persistent file-based databases.
     * Uses 'SCRIPT TO' inside a ZIP archive when running against an in-memory test database (H2 mem: mode).
     * Cleans up partial target files on failure.
     */
    private void executeH2Backup(File targetZip) {
        if (targetZip.getParentFile() != null && !targetZip.getParentFile().exists()) {
            targetZip.getParentFile().mkdirs();
        }
        if (targetZip.exists()) {
            targetZip.delete();
        }

        boolean isMemoryDb = false;
        try (java.sql.Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            if (url != null && url.contains(":mem:")) {
                isMemoryDb = true;
            }
        } catch (Exception e) {
            log.warn("Could not inspect database connection URL: {}", e.getMessage());
        }

        if (isMemoryDb) {
            createInMemoryDatabaseArchive(targetZip);
            return;
        }

        try {
            String escapedPath = targetZip.getAbsolutePath().replace("'", "''");
            jdbcTemplate.execute("BACKUP TO '" + escapedPath + "'");
            if (!targetZip.exists() || targetZip.length() == 0) {
                throw new IllegalStateException("H2 BACKUP TO command completed but target file was not created: " + targetZip.getAbsolutePath());
            }
        } catch (Exception e) {
            if (targetZip.exists()) {
                targetZip.delete();
            }
            throw e;
        }
    }

    private void createInMemoryDatabaseArchive(File targetZip) {
        File stagingDir = DataDirectoryResolver.resolveStagingDirectory();
        File scriptFile = new File(stagingDir, "in_memory_dump_" + UUID.randomUUID() + ".sql");
        try {
            jdbcTemplate.execute("SCRIPT TO '" + scriptFile.getAbsolutePath().replace("'", "''") + "'");
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(targetZip))) {
                zos.putNextEntry(new java.util.zip.ZipEntry("database.sql"));
                Files.copy(scriptFile.toPath(), zos);
                zos.closeEntry();
            }
            if (!targetZip.exists() || targetZip.length() == 0) {
                throw new IllegalStateException("In-memory database archive could not be created: " + targetZip.getAbsolutePath());
            }
        } catch (Exception e) {
            if (targetZip.exists()) {
                targetZip.delete();
            }
            throw new IllegalStateException("Failed to archive in-memory database: " + e.getMessage(), e);
        } finally {
            if (scriptFile.exists()) {
                scriptFile.delete();
            }
        }
    }

    /**
     * Diagnostic JSON snapshot retention policy:
     * - Keeps up to 14 generations
     * - Keeps cumulative size under 1 GB
     * - Strictly protects pre_restore_* files, autobackup_latest.json, and non-JSON files.
     */
    void applySnapshotRetention(File snapshotsDir, File currentSnapshot) {
        try {
            File[] jsonSnapshots = snapshotsDir.listFiles((dir, name) -> {
                String lower = name.toLowerCase();
                return lower.startsWith("diagnostic_snapshot_") && lower.endsWith(".json");
            });

            if (jsonSnapshots == null || jsonSnapshots.length == 0) {
                return;
            }

            // Sort oldest first
            Arrays.sort(jsonSnapshots, Comparator.comparingLong(File::lastModified));

            long totalBytes = 0;
            for (File f : jsonSnapshots) {
                totalBytes += f.length();
            }

            int count = jsonSnapshots.length;
            for (File snapshot : jsonSnapshots) {
                // Never delete the file that was just created
                if (snapshot.equals(currentSnapshot)) {
                    continue;
                }
                // Never delete safety snapshots
                if (snapshot.getName().startsWith("pre_restore_")) {
                    continue;
                }

                if (count > MAX_SNAPSHOT_GENERATIONS || totalBytes > MAX_SNAPSHOT_BYTES) {
                    long size = snapshot.length();
                    if (snapshot.delete()) {
                        log.info("Diagnostic snapshot retention pruned: {}", snapshot.getName());
                        totalBytes -= size;
                        count--;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Snapshot retention cleanup encountered a non-critical error: {}", e.getMessage());
        }
    }

    /**
     * Resolves streamed download payload for a given recovery ID without loading large files into memory.
     */
    public DownloadPayload getDownloadPayload(String recoveryId) throws Exception {
        if ("OFFSITE".equalsIgnoreCase(recoveryId)) {
            if (dataProtectionService == null || dataProtectionEntitlement == null) {
                throw new IllegalStateException("Data Protection service is unavailable for download");
            }
            String machineId = dataProtectionEntitlement.getMachineId();
            String licenseId = dataProtectionEntitlement.getLicenseId();
            byte[] decrypted = dataProtectionService.downloadAndDecryptBackup(machineId, licenseId);
            return new DownloadPayload("cloud_backup_" + machineId + ".json",
                    new ByteArrayResource(decrypted), decrypted.length, "application/json");
        }

        File file = DiagnosticRecoveryPathValidator.resolveAndValidateFile(recoveryId);
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Requested backup file not found: " + recoveryId);
        }

        String mimeType = file.getName().toLowerCase().endsWith(".zip") ? "application/zip" : "application/json";
        return new DownloadPayload(file.getName(), new FileSystemResource(file), file.length(), mimeType);
    }

    public static class DownloadPayload {
        private final String filename;
        private final Resource resource;
        private final long contentLength;
        private final String contentType;

        public DownloadPayload(String filename, Resource resource, long contentLength, String contentType) {
            this.filename = filename;
            this.resource = resource;
            this.contentLength = contentLength;
            this.contentType = contentType;
        }

        public String getFilename() {
            return filename;
        }

        public Resource getResource() {
            return resource;
        }

        public long getContentLength() {
            return contentLength;
        }

        public String getContentType() {
            return contentType;
        }
    }
}
