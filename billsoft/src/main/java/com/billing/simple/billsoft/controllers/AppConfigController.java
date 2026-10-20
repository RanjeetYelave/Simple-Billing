package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controller for managing application-level configurations and embedded state.
 * Enforces strict tenant boundaries, blocks sensitive credentials,
 * and maintains an explicit global exception for SNAKE_GAME_STATE.
 */
@RestController
@RequestMapping("/api/app-config")
@CrossOrigin
public class AppConfigController {

    private final AppConfigRepository appConfigRepo;
    private final AuthController authController;

    private static final String SNAKE_KEY = "SNAKE_GAME_STATE";

    private static final Set<String> BLOCKED_EXACT_KEYS = Set.of(
            "global_password",
            "backup_cloud_credentials",
            "backup_cloud_secret",
            "backup_cloud_access_key",
            "license_key"
    );

    private static final Pattern FIRM_KEY_PATTERN = Pattern.compile(".*_(\\d+)$");

    public AppConfigController(AppConfigRepository appConfigRepo, AuthController authController) {
        this.appConfigRepo = appConfigRepo;
        this.authController = authController;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String upper = key.trim().toUpperCase();
        if (BLOCKED_EXACT_KEYS.contains(key.trim().toLowerCase())) return true;
        if (key.trim().toLowerCase().startsWith("session_")) return true;
        if (upper.contains("PASSWORD")) return true;
        if (upper.contains("SECRET")) return true;
        if (upper.contains("CREDENTIAL")) return true;
        if (upper.contains("PIN")) return true;
        if (upper.contains("HASH")) return true;
        return upper.contains("TOKEN") && !upper.equals("SNAKE_GAME_STATE");
    }

    private boolean isSequenceCounter(String key) {
        if (key == null) return false;
        String upper = key.trim().toUpperCase();
        return upper.startsWith("LAST_") || upper.contains("_SEQ_");
    }

    private Long extractFirmIdFromKey(String key) {
        if (key == null) return null;
        Matcher m = FIRM_KEY_PATTERN.matcher(key.trim());
        if (m.matches()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    @GetMapping("/{key}")
    public ResponseEntity<Map<String, Object>> getConfig(@PathVariable("key") String key) {
        if (isSensitiveKey(key)) {
            Map<String, Object> err = new HashMap<>();
            err.put("key", key);
            err.put("error", "Access to sensitive configuration key is prohibited");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        // SNAKE_GAME_STATE: intentionally global across firms, accessible to authenticated sessions without X-Firm-Id
        if (SNAKE_KEY.equalsIgnoreCase(key)) {
            return readConfigDirectly(SNAKE_KEY);
        }

        // Tenant validation for firm-scoped keys
        Long keyFirmId = extractFirmIdFromKey(key);
        Long currentFirmId = TenantContext.getCurrentFirmId();
        if (keyFirmId != null && currentFirmId != null && !keyFirmId.equals(currentFirmId)) {
            Map<String, Object> err = new HashMap<>();
            err.put("key", key);
            err.put("error", "Cross-firm configuration access prohibited");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        return readConfigDirectly(key);
    }

    private ResponseEntity<Map<String, Object>> readConfigDirectly(String key) {
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
        if (isSensitiveKey(key)) {
            Map<String, Object> err = new HashMap<>();
            err.put("key", key);
            err.put("error", "Access to sensitive configuration key is prohibited");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        if ("auth_enabled".equalsIgnoreCase(key)) {
            Map<String, Object> err = new HashMap<>();
            err.put("key", key);
            err.put("error", "auth_enabled cannot be modified via generic configuration endpoint; use /api/auth/enable or /api/auth/disable");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        if (isSequenceCounter(key)) {
            Map<String, Object> err = new HashMap<>();
            err.put("key", key);
            err.put("error", "Sequence counters cannot be modified directly");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        if ("FACTORY_RESET_EPOCH".equalsIgnoreCase(key)) {
            Map<String, Object> err = new HashMap<>();
            err.put("key", key);
            err.put("error", "FACTORY_RESET_EPOCH cannot be modified directly");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        // SNAKE_GAME_STATE: intentionally global, bypasses tenant-scoping
        if (SNAKE_KEY.equalsIgnoreCase(key)) {
            return saveConfigDirectly(SNAKE_KEY, body);
        }

        // Tenant validation for firm-scoped keys
        Long keyFirmId = extractFirmIdFromKey(key);
        Long currentFirmId = TenantContext.getCurrentFirmId();
        if (keyFirmId != null && currentFirmId != null && !keyFirmId.equals(currentFirmId)) {
            Map<String, Object> err = new HashMap<>();
            err.put("key", key);
            err.put("error", "Cross-firm configuration mutation prohibited");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        return saveConfigDirectly(key, body);
    }

    private ResponseEntity<Map<String, Object>> saveConfigDirectly(String key, Map<String, Object> body) {
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
        if (isSensitiveKey(key)) {
            Map<String, Object> err = new HashMap<>();
            err.put("key", key);
            err.put("error", "Access to sensitive configuration key is prohibited");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        if ("auth_enabled".equalsIgnoreCase(key)) {
            Map<String, Object> err = new HashMap<>();
            err.put("key", key);
            err.put("error", "auth_enabled cannot be deleted via generic configuration endpoint");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        if (isSequenceCounter(key)) {
            Map<String, Object> err = new HashMap<>();
            err.put("key", key);
            err.put("error", "Sequence counters cannot be deleted directly");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

        // Tenant validation for firm-scoped keys
        Long keyFirmId = extractFirmIdFromKey(key);
        Long currentFirmId = TenantContext.getCurrentFirmId();
        if (keyFirmId != null && currentFirmId != null && !keyFirmId.equals(currentFirmId)) {
            Map<String, Object> err = new HashMap<>();
            err.put("key", key);
            err.put("error", "Cross-firm configuration deletion prohibited");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
        }

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
