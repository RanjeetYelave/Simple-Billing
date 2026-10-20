package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dataprotection.DataProtectionService;
import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dto.BackupInspectionDTO;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.BackupService;
import com.billing.simple.billsoft.util.PasswordUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST API for Data Protection operations (Status, Manual Sync, Cloud Restore).
 * Enforces authentication and gates destructive cloud restore behind master password authorization.
 */
@RestController
@RequestMapping("/api/dataprotection")
@CrossOrigin
public class DataProtectionController {

    private final DataProtectionService dataProtectionService;
    private final BackupService backupService;
    private final com.billing.simple.billsoft.service.AutoBackupService autoBackupService;
    private final AuthController authController;
    private final AppConfigRepository appConfigRepo;
    private final ObjectMapper mapper;

    public DataProtectionController(DataProtectionService dataProtectionService,
                                  BackupService backupService,
                                  com.billing.simple.billsoft.service.AutoBackupService autoBackupService,
                                  AuthController authController,
                                  AppConfigRepository appConfigRepo) {
        this.dataProtectionService = dataProtectionService;
        this.backupService = backupService;
        this.autoBackupService = autoBackupService;
        this.authController = authController;
        this.appConfigRepo = appConfigRepo;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
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
                || authController.verifyMasterPassword(masterPassword);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> statusMap = new HashMap<>(dataProtectionService.getStatusMap());
        // Sanitize credentials from response
        statusMap.remove("secretKey");
        statusMap.remove("accessKey");
        statusMap.remove("credentials");
        return ResponseEntity.ok(statusMap);
    }

    @PostMapping("/backup-now")
    public ResponseEntity<Map<String, Object>> runBackupNow(HttpServletRequest request) {
        String masterPassword = request.getHeader("X-Master-Password");
        if (masterPassword == null || masterPassword.isBlank()) {
            masterPassword = request.getParameter("masterPassword");
        }
        if (authController.isAuthEnabled() && !isMasterAuthorized(masterPassword)) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Triggering cloud backup requires privileged authorization");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        if (autoBackupService != null) {
            try {
                autoBackupService.runAutoBackup();
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> result = dataProtectionService.triggerBackupNow(true);
        return ResponseEntity.ok(result);
    }

    @RequestMapping(value = "/inspect-cloud", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> inspectCloudBackup(
            HttpServletRequest request,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(value = "machineId", required = false) String paramMachineId,
            @RequestParam(value = "licenseId", required = false) String paramLicenseId) {
        String masterPassword = request.getHeader("X-Master-Password");
        if (masterPassword == null || masterPassword.isBlank()) {
            masterPassword = request.getParameter("masterPassword");
            if ((masterPassword == null || masterPassword.isBlank()) && body != null) {
                masterPassword = body.get("masterPassword");
            }
        }
        if (authController.isAuthEnabled() && !isMasterAuthorized(masterPassword)) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Cloud inspection requires privileged authorization");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        try {
            String machineId = paramMachineId != null ? paramMachineId : (body != null ? body.get("machineId") : null);
            String licenseId = paramLicenseId != null ? paramLicenseId : (body != null ? body.get("licenseId") : null);
            byte[] decryptedBytes = dataProtectionService.downloadAndDecryptBackup(machineId, licenseId);
            BackupDTO backup = mapper.readValue(decryptedBytes, BackupDTO.class);
            BackupInspectionDTO inspection = backupService.inspectBackup(backup);
            return ResponseEntity.ok(inspection);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Cloud inspection failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/restore-cloud")
    public ResponseEntity<Map<String, Object>> restoreCloudBackup(
            HttpServletRequest request,
            @RequestParam("machineId") String machineId,
            @RequestParam("licenseId") String licenseId,
            @RequestParam(value = "firmIds", required = false) List<Long> firmIds,
            @RequestParam(value = "mode", defaultValue = "clone") String mode,
            @RequestParam(value = "targetFirmId", required = false) Long targetFirmId) {
        Long currentFirmId = TenantContext.getCurrentFirmId();
        if (targetFirmId != null && currentFirmId != null && !currentFirmId.equals(targetFirmId)) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Cross-firm cloud restore prohibited");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        String masterPassword = request.getHeader("X-Master-Password");
        if (masterPassword == null || masterPassword.isBlank()) {
            masterPassword = request.getParameter("masterPassword");
        }
        if (authController.isAuthEnabled() && !isMasterAuthorized(masterPassword)) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Cloud restore requires master password authorization");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        try {
            byte[] decryptedBytes = dataProtectionService.downloadAndDecryptBackup(machineId, licenseId);
            BackupDTO backup = mapper.readValue(decryptedBytes, BackupDTO.class);
            Set<Long> selectedFirmIds = (firmIds != null && !firmIds.isEmpty()) ? new java.util.HashSet<>(firmIds) : null;
            List<FirmDetails> restoredFirms = backupService.importSelectiveData(backup, selectedFirmIds, mode, targetFirmId);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Successfully restored " + restoredFirms.size() + " firm(s) from cloud data protection.");
            response.put("restoredFirms", restoredFirms);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Cloud restore failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
