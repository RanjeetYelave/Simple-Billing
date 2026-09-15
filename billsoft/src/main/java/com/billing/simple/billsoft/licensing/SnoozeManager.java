package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.licensing.model.LicensePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.time.Duration;
import java.time.Instant;

/**
 * Manages asymmetric snooze state for License and Data Protection alerts.
 * Enforces capping rules:
 * - License snooze cannot exceed license.expiresAt.
 * - Data Protection snooze is unbounded (can snooze for 30d, 90d, or permanently).
 * - All snoozes automatically reset when license revision increments (R' > R).
 */
public class SnoozeManager {

    private static final String SNOOZE_FILE_NAME = "snooze_state.json";

    private final File snoozeFile;
    private final ObjectMapper mapper;
    private SnoozeState currentState;

    public SnoozeManager() {
        this(LicensingConfig.getStorageDirectory());
    }

    public SnoozeManager(File storageDir) {
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        this.snoozeFile = new File(storageDir, SNOOZE_FILE_NAME);
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.currentState = loadState();
    }

    public synchronized SnoozeState loadState() {
        if (!snoozeFile.exists() || !snoozeFile.isFile()) {
            return new SnoozeState();
        }
        try {
            return mapper.readValue(snoozeFile, SnoozeState.class);
        } catch (Exception e) {
            return new SnoozeState();
        }
    }

    public synchronized void saveState(SnoozeState state) {
        this.currentState = state;
        try {
            mapper.writeValue(snoozeFile, state);
        } catch (Exception ignored) {
        }
    }

    /**
     * Resets active snoozes if the accepted license revision is newer than the revision when snoozed.
     */
    public synchronized void checkAndResetOnRevisionBump(int currentRevision) {
        if (currentState == null) {
            currentState = new SnoozeState();
        }
        boolean changed = false;
        if (currentRevision > currentState.getLicenseSnoozedRevision()) {
            currentState.setLicenseSnoozedUntil(null);
            currentState.setLicenseSnoozedRevision(currentRevision);
            changed = true;
        }
        if (currentRevision > currentState.getDpSnoozedRevision()) {
            currentState.setDpSnoozedUntil(null);
            currentState.setDpPermanentlySnoozed(false);
            currentState.setDpSnoozedRevision(currentRevision);
            changed = true;
        }
        if (changed) {
            saveState(currentState);
        }
    }

    /**
     * Snoozes license expiry reminder.
     * Duration cannot exceed remaining license term (capped to activeLicense.getExpiresAt()).
     * Supported duration codes: "1d", "3d", "7d", "1", "3", "7".
     */
    public synchronized Instant snoozeLicense(String durationCode, LicensePayload activeLicense) {
        if (activeLicense == null) {
            return null;
        }
        checkAndResetOnRevisionBump(activeLicense.getRevision());

        long days = parseDays(durationCode);
        Instant now = Instant.now();
        Instant target = now.plus(Duration.ofDays(days));

        // Enforce rule: License snooze cannot exceed remaining license term
        if (activeLicense.getExpiresAt() != null && target.isAfter(activeLicense.getExpiresAt())) {
            target = activeLicense.getExpiresAt();
        }

        currentState.setLicenseSnoozedUntil(target);
        currentState.setLicenseSnoozedRevision(activeLicense.getRevision());
        saveState(currentState);
        return target;
    }

    /**
     * Snoozes Data Protection add-on reminder.
     * Duration is unbounded (can freely exceed DP expiry term).
     * Supported duration codes: "1d", "3d", "7d", "30d", "90d", "3m", "permanent".
     */
    public synchronized Instant snoozeDataProtection(String durationCode, LicensePayload activeLicense) {
        int revision = activeLicense != null ? activeLicense.getRevision() : currentState.getDpSnoozedRevision();
        checkAndResetOnRevisionBump(revision);

        if ("permanent".equalsIgnoreCase(durationCode) || "perm".equalsIgnoreCase(durationCode)) {
            currentState.setDpPermanentlySnoozed(true);
            currentState.setDpSnoozedUntil(null);
            currentState.setDpSnoozedRevision(revision);
            saveState(currentState);
            return null;
        }

        long days = parseDays(durationCode);
        Instant target = Instant.now().plus(Duration.ofDays(days));
        currentState.setDpPermanentlySnoozed(false);
        currentState.setDpSnoozedUntil(target);
        currentState.setDpSnoozedRevision(revision);
        saveState(currentState);
        return target;
    }

    /**
     * True if license reminder is currently snoozed for the active revision.
     */
    public synchronized boolean isLicenseSnoozed(int currentRevision) {
        if (currentState == null) return false;
        if (currentRevision > currentState.getLicenseSnoozedRevision()) {
            return false;
        }
        return currentState.getLicenseSnoozedUntil() != null && Instant.now().isBefore(currentState.getLicenseSnoozedUntil());
    }

    /**
     * True if Data Protection reminder is currently snoozed for the active revision.
     */
    public synchronized boolean isDataProtectionSnoozed(int currentRevision) {
        if (currentState == null) return false;
        if (currentRevision > currentState.getDpSnoozedRevision()) {
            return false;
        }
        if (currentState.isDpPermanentlySnoozed()) {
            return true;
        }
        return currentState.getDpSnoozedUntil() != null && Instant.now().isBefore(currentState.getDpSnoozedUntil());
    }

    public synchronized SnoozeState getCurrentState() {
        return currentState;
    }

    private long parseDays(String code) {
        if (code == null || code.isBlank()) return 1;
        String clean = code.trim().toLowerCase();
        if (clean.endsWith("d")) clean = clean.substring(0, clean.length() - 1);
        if ("30".equals(clean) || "1m".equals(clean)) return 30;
        if ("90".equals(clean) || "3m".equals(clean)) return 90;
        try {
            return Long.parseLong(clean);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
