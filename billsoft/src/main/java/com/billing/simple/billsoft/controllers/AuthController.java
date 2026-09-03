package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.util.PasswordUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {

    private final AppConfigRepository appConfigRepo;
    private final FirmDetailsRepository firmDetailsRepo;

    private static final Map<String, LocalDateTime> ACTIVE_SESSIONS = new ConcurrentHashMap<>();

    public AuthController(AppConfigRepository appConfigRepo, FirmDetailsRepository firmDetailsRepo) {
        this.appConfigRepo = appConfigRepo;
        this.firmDetailsRepo = firmDetailsRepo;
    }

    public boolean isValidSessionToken(String token) {
        if (token == null || token.isBlank()) return false;
        LocalDateTime expiry = ACTIVE_SESSIONS.get(token);
        if (expiry != null) {
            if (expiry.isBefore(LocalDateTime.now())) {
                ACTIVE_SESSIONS.remove(token);
                try { appConfigRepo.deleteById("session_" + token); } catch (Exception ignored) {}
                return false;
            }
            return true;
        }
        // Check database persistence (survives system/application restarts)
        try {
            return appConfigRepo.findById("session_" + token)
                    .map(AppConfig::getConfigValue)
                    .map(expStr -> {
                        try {
                            LocalDateTime exp = LocalDateTime.parse(expStr);
                            if (exp.isBefore(LocalDateTime.now())) {
                                appConfigRepo.deleteById("session_" + token);
                                return false;
                            }
                            ACTIVE_SESSIONS.put(token, exp);
                            return true;
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    private String createSessionToken() {
        String token = UUID.randomUUID().toString();
        LocalDateTime expiry = LocalDateTime.now().plusDays(30);
        ACTIVE_SESSIONS.put(token, expiry);
        try {
            AppConfig sessionConfig = appConfigRepo.findById("session_" + token).orElse(new AppConfig());
            sessionConfig.setConfigKey("session_" + token);
            sessionConfig.setConfigValue(expiry.toString());
            appConfigRepo.save(sessionConfig);
        } catch (Exception ignored) {}
        return token;
    }

    private void invalidateAllSessions() {
        ACTIVE_SESSIONS.clear();
        try {
            appConfigRepo.findAll().stream()
                    .filter(c -> c.getConfigKey() != null && c.getConfigKey().startsWith("session_"))
                    .forEach(appConfigRepo::delete);
        } catch (Exception ignored) {}
    }

    private boolean isAuthEnabled() {
        return appConfigRepo.findById("auth_enabled")
                .map(AppConfig::getConfigValue)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    private String getGlobalPassword() {
        return appConfigRepo.findById("global_password")
                .map(AppConfig::getConfigValue)
                .orElse("");
    }

    private boolean checkPassword(String inputPassword, String storedPassword) {
        boolean valid = PasswordUtil.checkPassword(inputPassword, storedPassword);
        if (valid && storedPassword != null && !storedPassword.contains(":") && !storedPassword.isEmpty()) {
            // Auto-upgrade stored legacy plain text password to hashed
            try {
                AppConfig pwConfig = appConfigRepo.findById("global_password").orElse(new AppConfig());
                pwConfig.setConfigKey("global_password");
                pwConfig.setConfigValue(PasswordUtil.hashPassword(inputPassword));
                appConfigRepo.save(pwConfig);
            } catch (Exception ignored) {}
        }
        return valid;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> response = new HashMap<>();
        response.put("authEnabled", isAuthEnabled());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String password = request != null ? request.get("password") : null;
        Map<String, Object> response = new HashMap<>();

        if (isAuthEnabled()) {
            String globalPassword = getGlobalPassword();
            if (!checkPassword(password, globalPassword)) {
                response.put("success", false);
                response.put("message", "Invalid password");
                return ResponseEntity.badRequest().body(response);
            }
        }

        String token = createSessionToken();

        response.put("success", true);
        response.put("token", token);
        response.put("message", "Login successful");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/enable")
    public ResponseEntity<Map<String, Object>> enableAuth(@RequestBody Map<String, String> request) {
        String newPassword = request != null ? request.get("password") : null;
        Map<String, Object> response = new HashMap<>();

        if (newPassword == null || newPassword.isBlank()) {
            response.put("success", false);
            response.put("message", "Password cannot be empty");
            return ResponseEntity.badRequest().body(response);
        }

        AppConfig pwConfig = appConfigRepo.findById("global_password").orElse(new AppConfig());
        pwConfig.setConfigKey("global_password");
        pwConfig.setConfigValue(PasswordUtil.hashPassword(newPassword));
        appConfigRepo.save(pwConfig);

        AppConfig authConfig = appConfigRepo.findById("auth_enabled").orElse(new AppConfig());
        authConfig.setConfigKey("auth_enabled");
        authConfig.setConfigValue("true");
        appConfigRepo.save(authConfig);

        String token = createSessionToken();

        response.put("success", true);
        response.put("token", token);
        response.put("message", "Authentication enabled successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/disable")
    public ResponseEntity<Map<String, Object>> disableAuth(@RequestBody Map<String, String> request) {
        String password = request != null ? request.get("password") : null;
        Map<String, Object> response = new HashMap<>();

        String globalPassword = getGlobalPassword();
        if (!checkPassword(password, globalPassword)) {
            response.put("success", false);
            response.put("message", "Incorrect password");
            return ResponseEntity.badRequest().body(response);
        }

        AppConfig authConfig = appConfigRepo.findById("auth_enabled").orElse(new AppConfig());
        authConfig.setConfigKey("auth_enabled");
        authConfig.setConfigValue("false");
        appConfigRepo.save(authConfig);

        invalidateAllSessions();

        response.put("success", true);
        response.put("message", "Authentication disabled successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> request) {
        String oldPassword = request != null ? request.get("oldPassword") : null;
        String newPassword = request != null ? request.get("newPassword") : null;

        String globalPassword = getGlobalPassword();
        Map<String, Object> response = new HashMap<>();

        if (!checkPassword(oldPassword, globalPassword)) {
            response.put("success", false);
            response.put("message", "Incorrect old password");
            return ResponseEntity.badRequest().body(response);
        }

        if (newPassword == null || newPassword.isBlank()) {
            response.put("success", false);
            response.put("message", "New password cannot be empty");
            return ResponseEntity.badRequest().body(response);
        }

        AppConfig config = appConfigRepo.findById("global_password").orElse(new AppConfig());
        config.setConfigKey("global_password");
        config.setConfigValue(PasswordUtil.hashPassword(newPassword));
        appConfigRepo.save(config);

        String token = createSessionToken();

        response.put("success", true);
        response.put("token", token);
        response.put("message", "Password changed successfully");
        return ResponseEntity.ok(response);
    }

    // SHA-256 hash of master recovery key
    private static final String MASTER_KEY_HASH = "3680e811ded1a1831a688d594243e727a23d8bc801a9e2fa31279dba46b635ce";

    @PostMapping("/reset-password-master")
    public ResponseEntity<Map<String, Object>> resetPasswordMaster(@RequestBody Map<String, String> request) {
        String masterPassword = request.get("masterPassword");
        String newPassword = request.get("newPassword");
        Map<String, Object> response = new HashMap<>();

        if (!verifyMasterPassword(masterPassword)) {
            response.put("success", false);
            response.put("message", "Invalid master password");
            return ResponseEntity.badRequest().body(response);
        }

        if (newPassword == null || newPassword.isBlank()) {
            response.put("success", false);
            response.put("message", "New password cannot be empty");
            return ResponseEntity.badRequest().body(response);
        }

        AppConfig pwConfig = appConfigRepo.findById("global_password").orElse(new AppConfig());
        pwConfig.setConfigKey("global_password");
        pwConfig.setConfigValue(PasswordUtil.hashPassword(newPassword));
        appConfigRepo.save(pwConfig);

        AppConfig authConfig = appConfigRepo.findById("auth_enabled").orElse(new AppConfig());
        authConfig.setConfigKey("auth_enabled");
        authConfig.setConfigValue("true");
        appConfigRepo.save(authConfig);

        String token = createSessionToken();

        response.put("success", true);
        response.put("token", token);
        response.put("message", "Global password reset successfully");
        return ResponseEntity.ok(response);
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
