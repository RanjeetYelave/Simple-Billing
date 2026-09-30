package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for managing global application-level configurations and embedded state.
 * Firm-agnostic key-value store stored in the app_config database table.
 */
@RestController
@RequestMapping("/api/app-config")
@CrossOrigin
public class AppConfigController {

    private final AppConfigRepository appConfigRepo;

    public AppConfigController(AppConfigRepository appConfigRepo) {
        this.appConfigRepo = appConfigRepo;
    }

    @GetMapping("/{key}")
    public ResponseEntity<Map<String, Object>> getConfig(@PathVariable("key") String key) {
        try {
            Optional<AppConfig> config = appConfigRepo.findById(key);
            Map<String, Object> res = new HashMap<>();
            if (config.isPresent()) {
                res.put("key", config.get().getConfigKey());
                res.put("value", config.get().getConfigValue());
                res.put("exists", true);
            } else {
                res.put("key", key);
                res.put("value", null);
                res.put("exists", false);
            }
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("key", key);
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    @PutMapping("/{key}")
    public ResponseEntity<Map<String, Object>> setConfig(
            @PathVariable("key") String key,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            String value = body != null && body.containsKey("value") ? String.valueOf(body.get("value")) : "";
            AppConfig config = new AppConfig(key, value);
            appConfigRepo.save(config);

            Map<String, Object> res = new HashMap<>();
            res.put("key", key);
            res.put("value", value);
            res.put("saved", true);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("key", key);
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Map<String, Object>> deleteConfig(@PathVariable("key") String key) {
        try {
            appConfigRepo.deleteById(key);
            Map<String, Object> res = new HashMap<>();
            res.put("key", key);
            res.put("deleted", true);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("key", key);
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }
}
