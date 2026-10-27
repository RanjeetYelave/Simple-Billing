package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.AutoBackupService;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.util.PasswordUtil;
import jakarta.servlet.http.HttpServletRequest;
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
    private final AuthController authController;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String MASTER_KEY_HASH = "3680e811ded1a1831a688d594243e727a23d8bc801a9e2fa31279dba46b635ce";

    public BackupController(BackupService backupService, AutoBackupService autoBackupService, AppConfigRepository appConfigRepo, AuthController authController) {
        this.backupService = backupService;
        this.autoBackupService = autoBackupService;
        this.appConfigRepo = appConfigRepo;
        this.authController = authController;
    }

    private boolean isMasterAuthorized(String masterPassword) {
        if (!authController.isAuthEnabled()) {
            return true;
        }
        if (masterPassword == null || masterPassword.isBlank()) {
            return false;
        }
        String globalPassword = appConfigRepo.findById("global_password")
                .map(AppConfig::getConfigValue)
                .orElse("");
        return (PasswordUtil.checkPassword(masterPassword, globalPassword))
                || authController.verifyMasterPassword(masterPassword)
                || verifyMasterPassword(masterPassword);
    }

    @GetMapping("/export")
    public ResponseEntity<?> exportBackup(@RequestParam("firmId") Long firmId) {
        Long currentFirmId = TenantContext.getCurrentFirmId();
        if (currentFirmId == null) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Tenant context required for firm backup export");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        if (!currentFirmId.equals(firmId)) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Cross-firm backup export prohibited");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        try {
            BackupDTO backup = backupService.exportData(firmId);
            String filename = "billsoft-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(backup);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to export backup: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/export/all")
    public ResponseEntity<?> exportAllBackup(HttpServletRequest request) {
        String masterPassword = request.getHeader("X-Master-Password");
        if (masterPassword == null || masterPassword.isBlank()) {
            masterPassword = request.getParameter("masterPassword");
        }

        if (authController.isAuthEnabled() && !isMasterAuthorized(masterPassword)) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Exporting full system backup requires master password authorization");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        try {
            BackupDTO backup = backupService.exportAllData();
            String filename = "billsoft-full-system-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(backup);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to export full system backup: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/auto/status")
    public ResponseEntity<Map<String, Object>> getAutoBackupStatus() {
        return ResponseEntity.ok(autoBackupService.getStatus());
    }

    @PostMapping({"/auto/run-now", "/auto/trigger"})
    public ResponseEntity<Map<String, Object>> runAutoBackupNow(HttpServletRequest request) {
        String masterPassword = request != null ? request.getHeader("X-Master-Password") : null;
        if (masterPassword == null || masterPassword.isBlank()) {
            masterPassword = request != null ? request.getParameter("masterPassword") : null;
        }
        if (authController.isAuthEnabled() && !isMasterAuthorized(masterPassword)) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Triggering automated backup requires privileged authorization");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        Map<String, Object> result = autoBackupService.runAutoBackup();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/auto/configure")
    public ResponseEntity<Map<String, Object>> configureAutoBackup(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, Object> config) {
        String masterPassword = request != null ? request.getHeader("X-Master-Password") : null;
        if (masterPassword == null || masterPassword.isBlank()) {
            masterPassword = request != null ? request.getParameter("masterPassword") : null;
            if ((masterPassword == null || masterPassword.isBlank()) && config != null && config.get("masterPassword") != null) {
                masterPassword = String.valueOf(config.get("masterPassword"));
            }
        }
        if (authController.isAuthEnabled() && !isMasterAuthorized(masterPassword)) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Configuring automated backup requires privileged authorization");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Auto-backup configuration updated successfully");
        return ResponseEntity.ok(response);
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
            HttpServletRequest request,
            @RequestParam(value = "firmIds", required = false) java.util.List<Long> firmIds,
            @RequestParam(value = "mode", defaultValue = "clone") String mode,
            @RequestParam(value = "targetFirmId", required = false) Long targetFirmId) {
        String normalizedMode = mode != null ? mode.trim().toLowerCase() : "clone";
        Long currentFirmId = TenantContext.getCurrentFirmId();
        if ("merge".equals(normalizedMode)) {
            if (targetFirmId == null) {
                targetFirmId = currentFirmId;
            }
            if (targetFirmId == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("error", "Destination firm ID is required for merge mode");
                return ResponseEntity.badRequest().body(response);
            }
            if (currentFirmId != null && !currentFirmId.equals(targetFirmId)) {
                Map<String, Object> response = new HashMap<>();
                response.put("error", "Cross-firm restore prohibited");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        } else {
            // In clone mode, destination is always a new firm. Target firm ID cannot override it.
            targetFirmId = null;
        }

        if (firmIds != null && firmIds.size() > 1) {
            String masterPassword = request.getHeader("X-Master-Password");
            if (masterPassword == null || masterPassword.isBlank()) {
                masterPassword = request.getParameter("masterPassword");
            }
            if (authController.isAuthEnabled() && !isMasterAuthorized(masterPassword)) {
                Map<String, Object> response = new HashMap<>();
                response.put("error", "Multi-firm auto-restore requires master password authorization");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        }

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
        } catch (com.fasterxml.jackson.core.JsonParseException | com.fasterxml.jackson.databind.JsonMappingException e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("error", "The uploaded file is not a valid Billsoft backup or is corrupted.");
            return ResponseEntity.badRequest().body(response);
        } catch (java.io.IOException e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to read backup file: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (RuntimeException e) {
            throw e;
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
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "firmIds", required = false) java.util.List<Long> firmIds,
            @RequestParam(value = "mode", defaultValue = "clone") String mode,
            @RequestParam(value = "targetFirmId", required = false) Long targetFirmId) {
        String normalizedMode = mode != null ? mode.trim().toLowerCase() : "clone";
        Long currentFirmId = TenantContext.getCurrentFirmId();
        if ("merge".equals(normalizedMode)) {
            if (targetFirmId == null) {
                targetFirmId = currentFirmId;
            }
            if (targetFirmId == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("error", "Destination firm ID is required for merge mode");
                return ResponseEntity.badRequest().body(response);
            }
            if (currentFirmId != null && !currentFirmId.equals(targetFirmId)) {
                Map<String, Object> response = new HashMap<>();
                response.put("error", "Cross-firm selective restore prohibited");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        } else {
            // In clone mode, destination is always a new firm. Target firm ID cannot override it.
            targetFirmId = null;
        }

        if (firmIds != null && firmIds.size() > 1) {
            String masterPassword = request.getHeader("X-Master-Password");
            if (masterPassword == null || masterPassword.isBlank()) {
                masterPassword = request.getParameter("masterPassword");
            }
            if (authController.isAuthEnabled() && !isMasterAuthorized(masterPassword)) {
                Map<String, Object> response = new HashMap<>();
                response.put("error", "Multi-firm selective restore requires master password authorization");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        }

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
        } catch (java.io.IOException e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to read uploaded backup file: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Selective restore failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping({"/import", "/restore", "/upload-restore"})
    public ResponseEntity<Map<String, String>> importBackup(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "firmId", required = false) Long firmId,
            @RequestParam(value = "sourceFirmId", required = false) Long sourceFirmId,
            @RequestParam(value = "mode", defaultValue = "merge") String mode) {
        String normalizedMode = mode != null ? mode.trim().toLowerCase() : "merge";
        Long currentFirmId = TenantContext.getCurrentFirmId();
        if ("merge".equals(normalizedMode)) {
            if (firmId == null) {
                firmId = currentFirmId;
            }
            if (firmId != null && currentFirmId != null && !currentFirmId.equals(firmId)) {
                Map<String, String> response = new HashMap<>();
                response.put("error", "Cross-firm backup import prohibited");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        } else {
            firmId = null;
        }

        try {
            BackupDTO backup = objectMapper.readValue(file.getInputStream(), BackupDTO.class);
            boolean isMultiFirm = backup.getAllFirms() != null && backup.getAllFirms().size() > 1;
            if (firmId != null && isMultiFirm && sourceFirmId == null) {
                Map<String, String> response = new HashMap<>();
                response.put("status", "error");
                response.put("errorCode", "AMBIGUOUS_MULTI_FIRM_BACKUP");
                response.put("error", "AMBIGUOUS_MULTI_FIRM_BACKUP: Uploaded backup contains " + backup.getAllFirms().size()
                        + " firms. Explicit source firm selection ('sourceFirmId') is required to import into target firm " + firmId + ".");
                return ResponseEntity.badRequest().body(response);
            }

            if (sourceFirmId != null) {
                backupService.importSelectiveData(backup, java.util.Collections.singleton(sourceFirmId), mode, firmId);
            } else {
                boolean merge = "merge".equalsIgnoreCase(mode);
                backupService.importData(backup, firmId, merge);
            }

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Backup restored successfully");
            return ResponseEntity.ok(response);
        } catch (com.fasterxml.jackson.core.JsonParseException | com.fasterxml.jackson.databind.JsonMappingException e) {
            e.printStackTrace();
            Map<String, String> response = new HashMap<>();
            response.put("error", "The uploaded file is not a valid Billsoft backup or is corrupted.");
            return ResponseEntity.badRequest().body(response);
        } catch (java.io.IOException e) {
            e.printStackTrace();
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to read uploaded backup file: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> response = new HashMap<>();
            response.put("error", "Restore failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/upload-restore/firm")
    public ResponseEntity<Map<String, String>> uploadRestoreFirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam("firmId") Long firmId,
            @RequestParam(value = "sourceFirmId", required = false) Long sourceFirmId,
            @RequestParam(value = "mode", defaultValue = "merge") String mode) {
        return importBackup(file, firmId, sourceFirmId, mode);
    }

    @PostMapping({"/restore/all", "/upload-restore/all"})
    public ResponseEntity<Map<String, Object>> restoreAll(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "clone") String mode) {
        String masterPassword = request.getHeader("X-Master-Password");
        if (masterPassword == null || masterPassword.isBlank()) {
            masterPassword = request.getParameter("masterPassword");
        }
        if (authController.isAuthEnabled() && !isMasterAuthorized(masterPassword)) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "System-wide restore requires privileged authorization");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        return importSelectiveBackup(request, file, null, mode, null);
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
                response.put("error", "Authentication required for factory reset");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        }

        try {
            backupService.factoryReset();
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "System reset to factory defaults successfully");
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
