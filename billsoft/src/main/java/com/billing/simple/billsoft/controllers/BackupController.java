package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.service.AutoBackupService;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.util.PasswordUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/backup")
@CrossOrigin
public class BackupController {

    private final BackupService backupService;
    private final AutoBackupService autoBackupService;
    private final AppConfigRepository appConfigRepo;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String MASTER_KEY_HASH = "3680e811ded1a1831a688d594243e727a23d8bc801a9e2fa31279dba46b635ce";

    public BackupController(BackupService backupService, AutoBackupService autoBackupService, AppConfigRepository appConfigRepo) {
        this.backupService = backupService;
        this.autoBackupService = autoBackupService;
        this.appConfigRepo = appConfigRepo;
    }

    @GetMapping("/export")
    public ResponseEntity<BackupDTO> exportBackup(@RequestParam("firmId") Long firmId) {
        try {
            BackupDTO backup = backupService.exportData(firmId);
            
            String filename = "billsoft-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(backup);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to export backup: " + e.getMessage());
        }
    }

    @GetMapping("/export/all")
    public ResponseEntity<BackupDTO> exportAllBackup() {
        try {
            BackupDTO backup = backupService.exportAllData();
            String filename = "billsoft-full-system-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(backup);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to export full system backup: " + e.getMessage());
        }
    }

    @GetMapping("/auto/status")
    public ResponseEntity<Map<String, Object>> getAutoBackupStatus() {
        return ResponseEntity.ok(autoBackupService.getStatus());
    }

    @PostMapping("/auto/run-now")
    public ResponseEntity<Map<String, Object>> runAutoBackupNow() {
        Map<String, Object> result = autoBackupService.runAutoBackup();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/auto/download")
    public ResponseEntity<byte[]> downloadLatestAutoBackup() {
        try {
            java.io.File file = new java.io.File(autoBackupService.getBackupDirectory(), "autobackup_latest.json");
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            byte[] data = java.nio.file.Files.readAllBytes(file.toPath());
            String filename = "autobackup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/auto/inspect")
    public ResponseEntity<?> inspectAutoBackup() {
        try {
            java.io.File file = new java.io.File(autoBackupService.getBackupDirectory(), "autobackup_latest.json");
            if (!file.exists() || !file.isFile() || file.length() == 0) {
                Map<String, String> response = new HashMap<>();
                response.put("error", "No automated backup file found in the storage directory.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            BackupDTO backup = objectMapper.readValue(file, BackupDTO.class);
            com.billing.simple.billsoft.dto.BackupInspectionDTO inspection = backupService.inspectBackup(backup);
            return ResponseEntity.ok(inspection);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to inspect auto-backup: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/auto/restore")
    public ResponseEntity<Map<String, Object>> restoreAutoBackup(
            @RequestParam(value = "firmIds", required = false) java.util.List<Long> firmIds,
            @RequestParam(value = "mode", defaultValue = "clone") String mode,
            @RequestParam(value = "targetFirmId", required = false) Long targetFirmId) {
        try {
            java.io.File file = new java.io.File(autoBackupService.getBackupDirectory(), "autobackup_latest.json");
            if (!file.exists() || !file.isFile() || file.length() == 0) {
                Map<String, Object> response = new HashMap<>();
                response.put("error", "No automated backup file found in the storage directory.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            BackupDTO backup = objectMapper.readValue(file, BackupDTO.class);
            java.util.Set<Long> selectedFirmIds = (firmIds != null && !firmIds.isEmpty()) ? new java.util.HashSet<>(firmIds) : null;
            java.util.List<com.billing.simple.billsoft.entities.FirmDetails> restoredFirms =
                    backupService.importSelectiveData(backup, selectedFirmIds, mode, targetFirmId);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Successfully restored " + restoredFirms.size() + " firm(s) from auto-backup.");
            response.put("restoredFirms", restoredFirms);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Restore from auto-backup failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/inspect")
    public ResponseEntity<?> inspectBackup(@RequestParam("file") MultipartFile file) {
        try {
            BackupDTO backup = objectMapper.readValue(file.getInputStream(), BackupDTO.class);
            com.billing.simple.billsoft.dto.BackupInspectionDTO inspection = backupService.inspectBackup(backup);
            return ResponseEntity.ok(inspection);
        } catch (com.fasterxml.jackson.core.JsonParseException | com.fasterxml.jackson.databind.JsonMappingException e) {
            e.printStackTrace();
            Map<String, String> response = new HashMap<>();
            response.put("error", "The uploaded file is not a valid Billsoft backup or is corrupted.");
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> response = new HashMap<>();
            response.put("error", "Inspection failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/import/selective")
    public ResponseEntity<Map<String, Object>> importSelectiveBackup(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "firmIds", required = false) java.util.List<Long> firmIds,
            @RequestParam(value = "mode", defaultValue = "clone") String mode,
            @RequestParam(value = "targetFirmId", required = false) Long targetFirmId) {
        try {
            BackupDTO backup = objectMapper.readValue(file.getInputStream(), BackupDTO.class);
            java.util.Set<Long> selectedFirmIds = (firmIds != null && !firmIds.isEmpty()) ? new java.util.HashSet<>(firmIds) : null;
            java.util.List<com.billing.simple.billsoft.entities.FirmDetails> restoredFirms =
                    backupService.importSelectiveData(backup, selectedFirmIds, mode, targetFirmId);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Successfully restored " + restoredFirms.size() + " firm(s).");
            response.put("restoredFirms", restoredFirms);
            return ResponseEntity.ok(response);
        } catch (com.fasterxml.jackson.core.JsonParseException | com.fasterxml.jackson.databind.JsonMappingException e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("error", "The uploaded file is not a valid Billsoft backup or is corrupted.");
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Selective restore failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, String>> importBackup(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "firmId", required = false) Long firmId,
            @RequestParam(value = "mode", defaultValue = "merge") String mode) {
        try {
            BackupDTO backup = objectMapper.readValue(file.getInputStream(), BackupDTO.class);
            boolean merge = "merge".equalsIgnoreCase(mode);
            backupService.importData(backup, firmId, merge);

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Backup restored successfully");
            return ResponseEntity.ok(response);
        } catch (com.fasterxml.jackson.core.JsonParseException | com.fasterxml.jackson.databind.JsonMappingException e) {
            e.printStackTrace();
            Map<String, String> response = new HashMap<>();
            response.put("error", "The uploaded file is not a valid Billsoft backup or is corrupted.");
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> response = new HashMap<>();
            response.put("error", "Restore failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/factory-reset")
    public ResponseEntity<Map<String, String>> factoryReset(@RequestBody(required = false) Map<String, String> request) {
        String confirm = request != null ? request.get("confirm") : null;
        if (!"RESET".equalsIgnoreCase(confirm) && !"RESET SOFTWARE".equalsIgnoreCase(confirm)) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Confirmation token required ('confirm': 'RESET')");
            return ResponseEntity.badRequest().body(response);
        }

        boolean authEnabled = appConfigRepo.findById("auth_enabled")
                .map(AppConfig::getConfigValue)
                .map(Boolean::parseBoolean)
                .orElse(false);

        if (authEnabled) {
            String password = request != null ? request.get("password") : null;
            String masterPassword = request != null ? request.get("masterPassword") : null;
            String globalPassword = appConfigRepo.findById("global_password")
                    .map(AppConfig::getConfigValue)
                    .orElse("");

            boolean valid = (password != null && PasswordUtil.checkPassword(password, globalPassword))
                    || verifyMasterPassword(masterPassword)
                    || (password != null && verifyMasterPassword(password));
            if (!valid) {
                Map<String, String> response = new HashMap<>();
                response.put("error", "Unauthorized: Valid password required for factory reset");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
        }

        try {
            backupService.factoryReset();
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Factory reset completed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> response = new HashMap<>();
            response.put("error", "Factory reset failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    private boolean verifyMasterPassword(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return MASTER_KEY_HASH.equalsIgnoreCase(hex.toString());
        } catch (Exception e) {
            return false;
        }
    }
}
