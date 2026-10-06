package com.billing.simple.billsoft.recovery;

import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Diagnostic clearance authentication and emergency password recovery service.
 * Uses constant-time comparison against the system master key hash with brute-force rate-limiting.
 */
@Service
public class DiagnosticAuthService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticAuthService.class);

    // Constant SHA-256 hash of the master recovery key
    // Reuses the identical hash constant established in AuthController
    private static final String MASTER_KEY_HASH = "3680e811ded1a1831a688d594243e727a23d8bc801a9e2fa31279dba46b635ce";

    private final AppConfigRepository appConfigRepo;

    // Rate-limiting / brute force protection state
    private final AtomicInteger failedAttempts = new AtomicInteger(0);
    private volatile long lockUntilTimestamp = 0L;

    public DiagnosticAuthService(AppConfigRepository appConfigRepo) {
        this.appConfigRepo = appConfigRepo;
    }

    /**
     * Verifies the provided diagnostic clearance key using constant-time hash comparison
     * with brute-force delay.
     */
    public boolean verifyKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now < lockUntilTimestamp) {
            log.warn("Diagnostic authentication attempt rejected due to active rate-limit backoff.");
            return false;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] inputHash = md.digest(key.trim().getBytes(StandardCharsets.UTF_8));
            byte[] expectedHash = hexToBytes(MASTER_KEY_HASH);

            boolean matches = MessageDigest.isEqual(inputHash, expectedHash);

            if (matches) {
                failedAttempts.set(0);
                lockUntilTimestamp = 0L;
                return true;
            } else {
                int attempts = failedAttempts.incrementAndGet();
                if (attempts >= 5) {
                    // Exponential backoff up to 60 seconds
                    long backoffMs = Math.min(60000L, (long) Math.pow(2, attempts - 5) * 1000L);
                    lockUntilTimestamp = now + backoffMs;
                }
                // Modest intentional brute-force delay
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                log.warn("Diagnostic authentication failed (attempt {}).", attempts);
                return false;
            }
        } catch (Exception e) {
            log.error("Error during diagnostic key verification: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Resets rate-limiting cooldown and failed counter (for testing and administrative reset).
     */
    void resetRateLimit() {
        failedAttempts.set(0);
        lockUntilTimestamp = 0L;
    }

    /**
     * Diagnostic password reset operation.
     * Sets auth_enabled = false in app_config, preserving all other configuration.
     */
    public boolean resetPassword() {
        try {
            AppConfig authConfig = appConfigRepo.findById("auth_enabled").orElse(new AppConfig("auth_enabled", "false"));
            authConfig.setConfigKey("auth_enabled");
            authConfig.setConfigValue("false");
            appConfigRepo.save(authConfig);
            log.info("Diagnostic recovery reset auth_enabled to false successfully.");
            return true;
        } catch (Exception e) {
            log.error("Failed to reset auth_enabled in app_config: {}", e.getMessage(), e);
            return false;
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
