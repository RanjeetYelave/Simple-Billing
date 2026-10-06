package com.billing.simple.billsoft.recovery;

import com.billing.simple.billsoft.licensing.DataProtectionEntitlement;
import com.billing.simple.billsoft.dataprotection.DataProtectionService;
import com.billing.simple.billsoft.dataprotection.DataProtectionStatus;
import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dto.BackupInspectionDTO;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.util.DataDirectoryResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;

@Service
public class DiagnosticBackupScanner {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticBackupScanner.class);

    private final DataSource dataSource;
    private final FirmDetailsRepository firmDetailsRepo;
    private final CustomerRepository customerRepo;
    private final InvoiceRepository invoiceRepo;
    private final ProductRepository productRepo;
    private final BackupService backupService;
    private final DataProtectionService dataProtectionService;
    private final DataProtectionEntitlement dataProtectionEntitlement;
    private final ObjectMapper objectMapper;

    @Autowired
    public DiagnosticBackupScanner(DataSource dataSource,
                                   FirmDetailsRepository firmDetailsRepo,
                                   CustomerRepository customerRepo,
                                   InvoiceRepository invoiceRepo,
                                   ProductRepository productRepo,
                                   BackupService backupService,
                                   @Autowired(required = false) DataProtectionService dataProtectionService,
                                   @Autowired(required = false) DataProtectionEntitlement dataProtectionEntitlement,
                                   ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.firmDetailsRepo = firmDetailsRepo;
        this.customerRepo = customerRepo;
        this.invoiceRepo = invoiceRepo;
        this.productRepo = productRepo;
        this.backupService = backupService;
        this.dataProtectionService = dataProtectionService;
        this.dataProtectionEntitlement = dataProtectionEntitlement;
        this.objectMapper = objectMapper;
    }

    /**
     * Performs a non-mutating multi-tier backup discovery scan.
     */
    public Map<String, Object> scanAll() {
        Map<String, Object> scanResult = new HashMap<>();

        // Tier 1 — Live Database
        scanResult.put("tier1LiveDatabase", scanTier1LiveDatabase());

        // Tier 2 — Auto Backup
        scanResult.put("tier2AutoBackup", scanTier2AutoBackup());

        // Tier 2B — Diagnostic Snapshots
        List<Map<String, Object>> snapshots = scanTier2bDiagnosticSnapshots();
        scanResult.put("tier2bDiagnosticSnapshots", snapshots);
        scanResult.put("tier2bSummary", summarizeSnapshots(snapshots));

        // Tier 3 — Offsite Backup Status (Reads local status only; 0 network calls)
        scanResult.put("tier3OffsiteBackup", scanTier3OffsiteBackup());

        return scanResult;
    }

    private Map<String, Object> scanTier1LiveDatabase() {
        Map<String, Object> tier1 = new HashMap<>();
        tier1.put("tier", 1);
        tier1.put("name", "Live Database");

        try (Connection conn = dataSource.getConnection()) {
            tier1.put("reachable", true);
            tier1.put("status", "CONNECTED");
            tier1.put("firmCount", firmDetailsRepo.count());
            tier1.put("customerCount", customerRepo.count());
            tier1.put("invoiceCount", invoiceRepo.count());
            tier1.put("productCount", productRepo.count());
        } catch (Exception e) {
            log.warn("Tier 1 Live DB scan failed: {}", e.getMessage());
            tier1.put("reachable", false);
            tier1.put("status", "DISCONNECTED");
            tier1.put("error", e.getMessage());
        }
        return tier1;
    }

    private Map<String, Object> scanTier2AutoBackup() {
        Map<String, Object> tier2 = new HashMap<>();
        tier2.put("tier", 2);
        tier2.put("name", "Auto Backup");
        tier2.put("recoveryId", "AUTO_LATEST");

        File backupDir = DataDirectoryResolver.resolveBackupsDirectory();
        File latestBackup = new File(backupDir, "autobackup_latest.json");

        if (latestBackup.exists() && latestBackup.isFile()) {
            tier2.put("exists", true);
            tier2.put("filename", latestBackup.getName());
            tier2.put("sizeBytes", latestBackup.length());
            tier2.put("modifiedTimestamp", latestBackup.lastModified());
            tier2.put("modifiedFormatted", Instant.ofEpochMilli(latestBackup.lastModified()).toString());
            tier2.put("sha256", computeSha256(latestBackup));
        } else {
            tier2.put("exists", false);
            tier2.put("message", "No autobackup_latest.json discovered in " + backupDir.getAbsolutePath());
        }
        return tier2;
    }

    private Map<String, Object> summarizeSnapshots(List<Map<String, Object>> list) {
        Map<String, Object> summary = new HashMap<>();
        long totalBytes = 0;
        int validCount = 0;
        int invalidCount = 0;
        Long newest = null;
        Long oldest = null;

        for (Map<String, Object> item : list) {
            Object sizeObj = item.get("sizeBytes");
            if (sizeObj instanceof Number) {
                totalBytes += ((Number) sizeObj).longValue();
            }
            Object status = item.get("status");
            if ("VALID".equals(status)) {
                validCount++;
            } else {
                invalidCount++;
            }
            Object tsObj = item.get("modifiedTimestamp");
            if (tsObj instanceof Long) {
                long ts = (Long) tsObj;
                if (newest == null || ts > newest) newest = ts;
                if (oldest == null || ts < oldest) oldest = ts;
            }
        }

        summary.put("snapshotCount", list.size());
        summary.put("validCount", validCount);
        summary.put("invalidCount", invalidCount);
        summary.put("totalSizeBytes", totalBytes);
        summary.put("totalSizeFormatted", formatBytes(totalBytes));
        summary.put("retentionLimit", 14);
        summary.put("newestSnapshot", newest != null ? Instant.ofEpochMilli(newest).toString() : null);
        summary.put("oldestSnapshot", oldest != null ? Instant.ofEpochMilli(oldest).toString() : null);
        return summary;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.US, "%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private List<Map<String, Object>> scanTier2bDiagnosticSnapshots() {
        List<Map<String, Object>> list = new ArrayList<>();
        File snapshotsDir = DataDirectoryResolver.resolveDiagnosticSnapshotsDirectory();

        File[] files = snapshotsDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".json") || lower.endsWith(".zip");
        });

        if (files != null && files.length > 0) {
            // Sort newest first
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

            for (File f : files) {
                Map<String, Object> item = new HashMap<>();
                item.put("filename", f.getName());
                item.put("sizeBytes", f.length());
                item.put("modifiedTimestamp", f.lastModified());
                item.put("modifiedFormatted", Instant.ofEpochMilli(f.lastModified()).toString());

                if (f.getName().toLowerCase().endsWith(".zip")) {
                    item.put("type", "H2_SNAPSHOT");
                    item.put("format", "H2 ZIP");
                    item.put("recoveryId", "H2:" + f.getName());
                    if (f.length() < 100) {
                        item.put("status", "INVALID");
                        item.put("statusReason", "Incomplete H2 archive (" + f.length() + " B)");
                    } else {
                        item.put("status", "VALID");
                        item.put("statusReason", "Physical H2 database archive");
                    }
                    item.put("restorable", false);
                    item.put("restoreNote", "Physical archive requires offline restoration procedure");
                } else {
                    item.put("type", "JSON_SNAPSHOT");
                    item.put("format", "JSON");
                    item.put("recoveryId", "DIAGNOSTIC:" + f.getName());
                    item.put("sha256", computeSha256(f));

                    if (f.length() < 100) {
                        item.put("status", "INVALID");
                        item.put("statusReason", "Incomplete or empty snapshot payload (" + f.length() + " B)");
                        item.put("restorable", false);
                        item.put("restoreNote", "Invalid snapshot payload cannot be restored");
                    } else {
                        boolean valid = hasValidBackupMetadata(f);
                        if (valid) {
                            item.put("status", "VALID");
                            item.put("statusReason", "Valid JSON selective snapshot");
                            item.put("restorable", true);
                        } else {
                            item.put("status", "INVALID");
                            item.put("statusReason", "Missing required backup metadata");
                            item.put("restorable", false);
                            item.put("restoreNote", "Invalid snapshot payload cannot be restored");
                        }
                    }
                }
                list.add(item);
            }
        }
        return list;
    }

    private boolean hasValidBackupMetadata(File f) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(f);
            return root.hasNonNull("metadata");
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> scanTier3OffsiteBackup() {
        Map<String, Object> tier3 = new HashMap<>();
        tier3.put("tier", 3);
        tier3.put("name", "Offsite Data Protection");
        tier3.put("recoveryId", "OFFSITE");

        if (dataProtectionService == null) {
            tier3.put("available", false);
            tier3.put("message", "Data Protection service not initialized");
            return tier3;
        }

        DataProtectionStatus status = dataProtectionService.getCurrentStatus();
        Map<String, Object> statusMap = dataProtectionService.getStatusMap();

        tier3.put("available", true);
        tier3.put("enabled", statusMap.get("enabled"));
        tier3.put("active", statusMap.get("active"));
        tier3.put("expiresAt", statusMap.get("expiresAt"));
        tier3.put("lastSuccessfulBackup", status != null ? status.getLastSuccessfulCloudBackupAt() : null);
        tier3.put("sizeBytes", status != null ? status.getLastBackupSizeBytes() : 0);
        tier3.put("blobSha", status != null ? status.getLastBlobSha() : null);
        tier3.put("lastError", status != null ? status.getLastError() : null);

        return tier3;
    }

    /**
     * In-memory inspection of a backup without modifying any database or persistent state.
     */
    public Object inspect(String recoveryId) throws Exception {
        if ("OFFSITE".equalsIgnoreCase(recoveryId)) {
            if (dataProtectionService == null || dataProtectionEntitlement == null) {
                throw new IllegalStateException("Data Protection service is unavailable");
            }
            String machineId = dataProtectionEntitlement.getMachineId();
            String licenseId = dataProtectionEntitlement.getLicenseId();
            byte[] decrypted = dataProtectionService.downloadAndDecryptBackup(machineId, licenseId);
            BackupDTO dto = objectMapper.readValue(decrypted, BackupDTO.class);
            return backupService.inspectBackup(dto);
        }

        File targetFile = DiagnosticRecoveryPathValidator.resolveAndValidateFile(recoveryId);
        if (targetFile == null || !targetFile.exists() || !targetFile.isFile()) {
            throw new IllegalArgumentException("Backup file does not exist: " + recoveryId);
        }

        if (targetFile.getName().toLowerCase().endsWith(".zip")) {
            // H2 native binary snapshot
            Map<String, Object> h2Summary = new HashMap<>();
            h2Summary.put("type", "H2_SNAPSHOT");
            h2Summary.put("format", "H2 ZIP");
            h2Summary.put("filename", targetFile.getName());
            h2Summary.put("sizeBytes", targetFile.length());
            h2Summary.put("modifiedTimestamp", targetFile.lastModified());
            h2Summary.put("modifiedFormatted", Instant.ofEpochMilli(targetFile.lastModified()).toString());
            h2Summary.put("status", targetFile.length() < 100 ? "INVALID" : "VALID");
            h2Summary.put("restorable", false);
            h2Summary.put("description", "H2 Native Database Binary Archive (Physical snapshot - requires offline restoration)");
            return h2Summary;
        }

        // Local JSON backup
        if (targetFile.length() < 100) {
            Map<String, Object> invSummary = new HashMap<>();
            invSummary.put("type", "JSON_SNAPSHOT");
            invSummary.put("format", "JSON");
            invSummary.put("filename", targetFile.getName());
            invSummary.put("sizeBytes", targetFile.length());
            invSummary.put("status", "INVALID");
            invSummary.put("statusReason", "Incomplete or empty snapshot payload (" + targetFile.length() + " B)");
            invSummary.put("restorable", false);
            invSummary.put("description", "Invalid diagnostic snapshot: insufficient payload size");
            return invSummary;
        }

        try {
            BackupDTO backupDto = objectMapper.readValue(targetFile, BackupDTO.class);
            if (backupDto.getMetadata() == null) {
                Map<String, Object> invSummary = new HashMap<>();
                invSummary.put("type", "JSON_SNAPSHOT");
                invSummary.put("format", "JSON");
                invSummary.put("filename", targetFile.getName());
                invSummary.put("sizeBytes", targetFile.length());
                invSummary.put("status", "INVALID");
                invSummary.put("statusReason", "Missing backup metadata (invalid structure)");
                invSummary.put("restorable", false);
                return invSummary;
            }
            return backupService.inspectBackup(backupDto);
        } catch (Exception e) {
            Map<String, Object> corruptSummary = new HashMap<>();
            corruptSummary.put("type", "JSON_SNAPSHOT");
            corruptSummary.put("format", "JSON");
            corruptSummary.put("filename", targetFile.getName());
            corruptSummary.put("sizeBytes", targetFile.length());
            corruptSummary.put("status", "CORRUPTED");
            corruptSummary.put("statusReason", "JSON parsing failure: " + e.getMessage());
            corruptSummary.put("restorable", false);
            return corruptSummary;
        }
    }

    public static String computeSha256(File file) {
        if (file == null || !file.exists() || !file.isFile()) return null;
        try (InputStream is = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to compute SHA-256 for file {}: {}", file.getName(), e.getMessage());
            return null;
        }
    }
}
