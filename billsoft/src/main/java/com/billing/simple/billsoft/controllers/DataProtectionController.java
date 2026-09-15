package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dataprotection.DataProtectionService;
import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dto.BackupInspectionDTO;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.service.BackupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST API for Data Protection operations (Status, Manual Sync, Cloud Restore).
 */
@RestController
@RequestMapping("/api/dataprotection")
@CrossOrigin
public class DataProtectionController {

    private final DataProtectionService dataProtectionService;
    private final BackupService backupService;
    private final ObjectMapper mapper;

    public DataProtectionController(DataProtectionService dataProtectionService, BackupService backupService) {
        this.dataProtectionService = dataProtectionService;
        this.backupService = backupService;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(dataProtectionService.getStatusMap());
    }

    @PostMapping("/backup-now")
    public ResponseEntity<Map<String, Object>> runBackupNow() {
        Map<String, Object> result = dataProtectionService.triggerBackupNow(true);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/inspect-cloud")
    public ResponseEntity<?> inspectCloudBackup(@RequestBody Map<String, String> body) {
        try {
            String machineId = body.get("machineId");
            String licenseId = body.get("licenseId");
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
            @RequestParam("machineId") String machineId,
            @RequestParam("licenseId") String licenseId,
            @RequestParam(value = "firmIds", required = false) List<Long> firmIds,
            @RequestParam(value = "mode", defaultValue = "clone") String mode,
            @RequestParam(value = "targetFirmId", required = false) Long targetFirmId) {
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
