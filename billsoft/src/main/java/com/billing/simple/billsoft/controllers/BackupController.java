package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.service.BackupService;
import org.springframework.http.HttpHeaders;
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
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
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
}
