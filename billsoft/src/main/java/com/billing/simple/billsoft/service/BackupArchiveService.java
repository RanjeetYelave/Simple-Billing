package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.exception.BackupValidationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class BackupArchiveService {

    private static final Logger log = LoggerFactory.getLogger(BackupArchiveService.class);
    private static final int BUFFER_SIZE = 8192;

    private final ObjectMapper objectMapper;
    private final BackupValidationService backupValidationService;

    public BackupArchiveService(ObjectMapper objectMapper, BackupValidationService backupValidationService) {
        this.objectMapper = objectMapper;
        this.backupValidationService = backupValidationService;
    }

    /**
     * Reads and validates an uploaded backup file stream (either .json or .zip).
     * Enforces checksum verification, ZIP integrity, ZipSlip protection, and schema validation.
     */
    public BackupDTO extractAndValidateBackup(InputStream inputStream, String originalFilename) throws IOException {
        if (inputStream == null) {
            throw new BackupValidationException("MALFORMED_BACKUP", "Backup input stream cannot be null.");
        }

        byte[] allBytes = inputStream.readAllBytes();
        if (allBytes.length == 0) {
            throw new BackupValidationException("EMPTY_BACKUP", "The provided backup file is empty.");
        }

        boolean isZip = isZipArchive(allBytes, originalFilename);
        BackupDTO backup;

        if (isZip) {
            backup = extractFromZipArchive(allBytes);
        } else {
            try {
                backup = objectMapper.readValue(allBytes, BackupDTO.class);
            } catch (JsonParseException | JsonMappingException e) {
                throw new BackupValidationException("MALFORMED_BACKUP",
                        "The uploaded file is not a valid RupeeCRM backup or is corrupted: " + e.getOriginalMessage());
            }
        }

        if (backup == null) {
            throw new BackupValidationException("MALFORMED_BACKUP", "Backup payload could not be deserialized.");
        }

        // Run comprehensive structural and entity integrity validation
        backupValidationService.validate(backup);
        return backup;
    }

    /**
     * Creates a validated ZIP archive containing backup.json and manifest.json with SHA-256 integrity checksum.
     */
    public byte[] createZipArchive(BackupDTO backup) throws IOException {
        if (backup == null) {
            throw new IllegalArgumentException("BackupDTO cannot be null for zip export.");
        }

        // 1. Serialize backup JSON
        byte[] backupJsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(backup);
        String backupJsonSha256 = calculateSha256(backupJsonBytes);

        // 2. Build manifest
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("formatVersion", "2.0");
        manifest.put("archiveType", "RUPEECRM_FULL_BACKUP_ZIP");
        manifest.put("createdAt", LocalDateTime.now().toString());
        manifest.put("backupJsonSha256", backupJsonSha256);
        manifest.put("backupJsonSizeBytes", backupJsonBytes.length);

        if (backup.getMetadata() != null) {
            manifest.put("metadata", backup.getMetadata());
        }

        Map<String, Integer> recordCounts = new LinkedHashMap<>();
        if (backup.getAllFirms() != null) recordCounts.put("firms", backup.getAllFirms().size());
        if (backup.getCustomers() != null) recordCounts.put("customers", backup.getCustomers().size());
        if (backup.getProducts() != null) recordCounts.put("products", backup.getProducts().size());
        if (backup.getInvoices() != null) recordCounts.put("invoices", backup.getInvoices().size());
        if (backup.getNotifications() != null) recordCounts.put("notifications", backup.getNotifications().size());
        manifest.put("recordCounts", recordCounts);

        byte[] manifestBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);

        // 3. Write ZIP
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // manifest.json
            ZipEntry manifestEntry = new ZipEntry("manifest.json");
            zos.putNextEntry(manifestEntry);
            zos.write(manifestBytes);
            zos.closeEntry();

            // backup.json
            ZipEntry backupEntry = new ZipEntry("backup.json");
            zos.putNextEntry(backupEntry);
            zos.write(backupJsonBytes);
            zos.closeEntry();
        }

        return baos.toByteArray();
    }

    private boolean isZipArchive(byte[] bytes, String filename) {
        if (filename != null && filename.toLowerCase().endsWith(".zip")) {
            return true;
        }
        // Check magic bytes PK\003\004
        return bytes.length >= 4 && bytes[0] == 0x50 && bytes[1] == 0x4B && bytes[2] == 0x03 && bytes[3] == 0x04;
    }

    private BackupDTO extractFromZipArchive(byte[] zipBytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // ZipSlip guard
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    throw new BackupValidationException("MALFORMED_BACKUP", "Malicious or invalid zip path: " + name);
                }
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream entryBaos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        entryBaos.write(buffer, 0, len);
                    }
                    entries.put(name, entryBaos.toByteArray());
                }
                zis.closeEntry();
            }
        } catch (ZipException ze) {
            throw new BackupValidationException("CORRUPT_ARCHIVE", "Corrupted or unreadable ZIP archive: " + ze.getMessage());
        } catch (EOFException eofe) {
            throw new BackupValidationException("CORRUPT_ARCHIVE", "Truncated ZIP archive: " + eofe.getMessage());
        }

        if (entries.isEmpty()) {
            throw new BackupValidationException("CORRUPT_ARCHIVE", "ZIP archive contains no entries.");
        }

        // Check if manifest.json exists
        byte[] manifestBytes = entries.get("manifest.json");
        Map<?, ?> manifestMap = null;
        if (manifestBytes != null) {
            try {
                manifestMap = objectMapper.readValue(manifestBytes, Map.class);
            } catch (Exception e) {
                log.warn("Could not parse manifest.json in backup ZIP: {}", e.getMessage());
            }
        }

        // Find backup JSON entry
        byte[] backupJsonBytes = entries.get("backup.json");
        if (backupJsonBytes == null) {
            // Check for rupeecrm-backup.json or any *.json file in root
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                if (!e.getKey().contains("/") && e.getKey().toLowerCase().endsWith(".json") && !e.getKey().equalsIgnoreCase("manifest.json")) {
                    backupJsonBytes = e.getValue();
                    break;
                }
            }
        }

        if (backupJsonBytes == null) {
            throw new BackupValidationException("CORRUPT_ARCHIVE", "Archive is missing required backup.json data file.");
        }

        // Verify SHA-256 checksum if declared in manifest
        if (manifestMap != null && manifestMap.containsKey("backupJsonSha256")) {
            String expectedSha256 = String.valueOf(manifestMap.get("backupJsonSha256")).trim();
            String actualSha256 = calculateSha256(backupJsonBytes);
            if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
                throw new BackupValidationException("CHECKSUM_MISMATCH",
                        "Backup archive checksum mismatch! Expected SHA-256: " + expectedSha256 + ", but calculated: " + actualSha256);
            }
        }

        // Verify required attachments if declared in manifest
        if (manifestMap != null && manifestMap.containsKey("requiredAttachments")) {
            Object attachmentsObj = manifestMap.get("requiredAttachments");
            if (attachmentsObj instanceof Collection<?> reqAtts) {
                for (Object item : reqAtts) {
                    String attName = String.valueOf(item);
                    if (!entries.containsKey(attName) && !entries.containsKey("attachments/" + attName)) {
                        throw new BackupValidationException("MISSING_ATTACHMENT",
                                "Backup archive is missing required attachment file: " + attName);
                    }
                }
            }
        }

        try {
            return objectMapper.readValue(backupJsonBytes, BackupDTO.class);
        } catch (Exception e) {
            throw new BackupValidationException("MALFORMED_BACKUP", "Failed to deserialize backup.json from archive: " + e.getMessage());
        }
    }

    public static String calculateSha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
