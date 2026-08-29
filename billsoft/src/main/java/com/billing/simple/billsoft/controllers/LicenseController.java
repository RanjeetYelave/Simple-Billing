package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/license")
@CrossOrigin
public class LicenseController {

    private final AppConfigRepository appConfigRepo;
    private final FirmDetailsRepository firmDetailsRepo;

    public LicenseController(AppConfigRepository appConfigRepo, FirmDetailsRepository firmDetailsRepo) {
        this.appConfigRepo = appConfigRepo;
        this.firmDetailsRepo = firmDetailsRepo;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        
        boolean hasFirm = firmDetailsRepo.count() > 0;
        response.put("hasFirm", hasFirm);

        String status = appConfigRepo.findById("license_status")
                .map(AppConfig::getConfigValue)
                .orElse("trial");

        String trialStartStr = appConfigRepo.findById("trial_start_date")
                .map(AppConfig::getConfigValue)
                .orElse(null);

        long trialDaysRemaining = 30;
        if (trialStartStr != null) {
            try {
                LocalDate startDate = LocalDate.parse(trialStartStr);
                LocalDate today = LocalDate.now();
                long daysPassed = ChronoUnit.DAYS.between(startDate, today);
                trialDaysRemaining = 30 - daysPassed;
                if (trialDaysRemaining < 0) {
                    trialDaysRemaining = 0;
                }
            } catch (Exception e) {
                // ignore
            }
        }

        if ("trial".equals(status) && trialDaysRemaining <= 0) {
            status = "expired";
        }

        response.put("status", status);
        response.put("trialDaysRemaining", trialDaysRemaining);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/activate")
    public ResponseEntity<Map<String, Object>> activate(@RequestBody Map<String, String> request) {
        String key = request.get("productKey");
        Map<String, Object> response = new HashMap<>();

        if (key != null && "saidarshan".equalsIgnoreCase(key.trim())) {
            AppConfig statusConfig = appConfigRepo.findById("license_status").orElse(new AppConfig());
            statusConfig.setConfigKey("license_status");
            statusConfig.setConfigValue("activated");
            appConfigRepo.save(statusConfig);

            response.put("success", true);
            response.put("message", "Product activated permanently!");
            return ResponseEntity.ok(response);
        }

        response.put("success", false);
        response.put("message", "Invalid product key");
        return ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/init-trial")
    public ResponseEntity<Map<String, Object>> initTrial() {
        Map<String, Object> response = new HashMap<>();

        AppConfig startConfig = appConfigRepo.findById("trial_start_date").orElse(new AppConfig());
        if (startConfig.getConfigValue() == null) {
            startConfig.setConfigKey("trial_start_date");
            startConfig.setConfigValue(LocalDate.now().toString());
            appConfigRepo.save(startConfig);
        }

        AppConfig statusConfig = appConfigRepo.findById("license_status").orElse(new AppConfig());
        if (statusConfig.getConfigValue() == null) {
            statusConfig.setConfigKey("license_status");
            statusConfig.setConfigValue("trial");
            appConfigRepo.save(statusConfig);
        }

        response.put("success", true);
        return ResponseEntity.ok(response);
    }
}
