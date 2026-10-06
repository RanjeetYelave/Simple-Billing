package com.billing.simple.billsoft.recovery;

import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages server-side diagnostic mode and persistent live console session state.
 * Enforces authoritative 8-hour automatic expiry and automatic VERBOSE logging synchronization.
 * State is safely persisted in the database (app_config) so it survives application navigation and restarts.
 */
@Service
public class DiagnosticSessionService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticSessionService.class);
    private static final String CONFIG_KEY_ENABLED = "diagnostic_console_enabled";
    private static final String CONFIG_KEY_LOG_LEVEL = "diagnostic_console_log_level";
    private static final String CONFIG_KEY_EXPIRES_AT = "diagnostic_console_expires_at";

    public static final long EIGHT_HOURS_MILLIS = 8L * 60L * 60L * 1000L;

    private final AtomicBoolean persistentConsoleEnabled = new AtomicBoolean(false);
    private volatile String logLevel = "NORMAL";
    private volatile long expiresAt = 0L;

    private final AppConfigRepository appConfigRepo;
    private final DiagnosticLogService logService;

    public DiagnosticSessionService(AppConfigRepository appConfigRepo, DiagnosticLogService logService) {
        this.appConfigRepo = appConfigRepo;
        this.logService = logService;
        try {
            appConfigRepo.findById(CONFIG_KEY_ENABLED).ifPresent(cfg -> {
                persistentConsoleEnabled.set("true".equalsIgnoreCase(cfg.getConfigValue()));
            });
            appConfigRepo.findById(CONFIG_KEY_LOG_LEVEL).ifPresent(cfg -> {
                if (cfg.getConfigValue() != null && !cfg.getConfigValue().isBlank()) {
                    logLevel = cfg.getConfigValue().toUpperCase();
                }
            });
            appConfigRepo.findById(CONFIG_KEY_EXPIRES_AT).ifPresent(cfg -> {
                if (cfg.getConfigValue() != null && !cfg.getConfigValue().isBlank()) {
                    try {
                        expiresAt = Long.parseLong(cfg.getConfigValue().trim());
                    } catch (NumberFormatException ignored) {}
                }
            });

            // Check if diagnostic session expired during application downtime
            if (persistentConsoleEnabled.get()) {
                if (expiresAt > 0 && System.currentTimeMillis() >= expiresAt) {
                    log.info("Diagnostic session expired during downtime (expiresAt={}, now={}). Restoring NORMAL logging.", expiresAt, System.currentTimeMillis());
                    disableDiagnosticsInternal();
                } else if ("VERBOSE".equalsIgnoreCase(logLevel)) {
                    logService.setVerboseLogging(true);
                }
            }
        } catch (Exception e) {
            log.warn("Could not load persistent diagnostic session config: {}", e.getMessage());
        }
    }

    /**
     * Checks if the active diagnostic session has exceeded its 8-hour window.
     * Automatically downgrades to disabled and NORMAL logging upon expiry.
     */
    public synchronized boolean checkExpiry() {
        if (persistentConsoleEnabled.get()) {
            if (expiresAt > 0 && System.currentTimeMillis() >= expiresAt) {
                log.info("System Diagnostics Mode reached 8-hour expiry (expiresAt={}, now={}). Auto-deactivating.", expiresAt, System.currentTimeMillis());
                disableDiagnosticsInternal();
                return true;
            }
        }
        return false;
    }

    private synchronized void disableDiagnosticsInternal() {
        persistentConsoleEnabled.set(false);
        this.logLevel = "NORMAL";
        this.expiresAt = 0L;
        persistConfig(CONFIG_KEY_ENABLED, "false");
        persistConfig(CONFIG_KEY_LOG_LEVEL, "NORMAL");
        persistConfig(CONFIG_KEY_EXPIRES_AT, "0");
        logService.setVerboseLogging(false);
        log.info("System Diagnostics Mode deactivated. Verbose logging turned OFF.");
    }

    public boolean isPersistentConsoleEnabled() {
        if (checkExpiry()) {
            return false;
        }
        return persistentConsoleEnabled.get();
    }

    /**
     * Explicitly enables or disables System Diagnostics Mode.
     * When enabled: creates an 8-hour window and activates VERBOSE logging.
     * When disabled: clears window and restores NORMAL logging.
     */
    public synchronized void setPersistentConsoleEnabled(boolean enabled) {
        if (enabled) {
            this.expiresAt = System.currentTimeMillis() + EIGHT_HOURS_MILLIS;
            this.logLevel = "VERBOSE";
            persistentConsoleEnabled.set(true);
            persistConfig(CONFIG_KEY_ENABLED, "true");
            persistConfig(CONFIG_KEY_LOG_LEVEL, "VERBOSE");
            persistConfig(CONFIG_KEY_EXPIRES_AT, String.valueOf(this.expiresAt));
            logService.setVerboseLogging(true);
            log.info("System Diagnostics Mode explicitly ENABLED for 8 hours (expiresAt={}). VERBOSE logging activated.", this.expiresAt);
        } else {
            disableDiagnosticsInternal();
        }
    }

    public long getExpiresAt() {
        checkExpiry();
        return expiresAt;
    }

    public String getLogLevel() {
        checkExpiry();
        return logLevel;
    }

    public void setLogLevel(String level) {
        if (checkExpiry()) return;
        if (level == null || level.isBlank()) {
            this.logLevel = "NORMAL";
        } else {
            this.logLevel = level.toUpperCase();
        }
        persistConfig(CONFIG_KEY_LOG_LEVEL, this.logLevel);
        if (persistentConsoleEnabled.get()) {
            logService.setVerboseLogging("VERBOSE".equalsIgnoreCase(this.logLevel));
        }
        log.info("Diagnostic log level set to: {}", this.logLevel);
    }

    public Map<String, Object> getPublicStatus() {
        checkExpiry();
        Map<String, Object> status = new HashMap<>();
        boolean active = persistentConsoleEnabled.get();
        status.put("enabled", active);
        status.put("logLevel", logLevel);
        status.put("expiresAt", active ? expiresAt : 0L);
        long remaining = (active && expiresAt > 0) ? Math.max(0L, expiresAt - System.currentTimeMillis()) : 0L;
        status.put("remainingMillis", remaining);
        return status;
    }

    /**
     * Package-private test helper to verify custom expiry intervals without sleeping 8 hours.
     */
    synchronized void setPersistentConsoleStateForTesting(boolean enabled, String level, long customExpiresAt) {
        this.persistentConsoleEnabled.set(enabled);
        this.logLevel = level != null ? level.toUpperCase() : "NORMAL";
        this.expiresAt = customExpiresAt;
        persistConfig(CONFIG_KEY_ENABLED, String.valueOf(enabled));
        persistConfig(CONFIG_KEY_LOG_LEVEL, this.logLevel);
        persistConfig(CONFIG_KEY_EXPIRES_AT, String.valueOf(this.expiresAt));
        if (enabled && "VERBOSE".equalsIgnoreCase(this.logLevel)) {
            logService.setVerboseLogging(true);
        } else {
            logService.setVerboseLogging(false);
        }
    }

    private void persistConfig(String key, String value) {
        try {
            AppConfig cfg = appConfigRepo.findById(key).orElse(new AppConfig(key, value));
            cfg.setConfigKey(key);
            cfg.setConfigValue(value);
            appConfigRepo.save(cfg);
        } catch (Exception e) {
            log.warn("Failed to persist diagnostic session config {}: {}", key, e.getMessage());
        }
    }
}

