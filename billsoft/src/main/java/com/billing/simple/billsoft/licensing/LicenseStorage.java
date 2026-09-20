package com.billing.simple.billsoft.licensing;

import com.billing.simple.billsoft.licensing.model.CustomerMessage;
import com.billing.simple.billsoft.licensing.model.CustomerMessageEnvelope;
import com.billing.simple.billsoft.licensing.model.LicensePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local file storage manager for license payload, sync state, and inbox messages.
 */
public class LicenseStorage {

    private static final String LICENSE_FILE_NAME = "license.lic";
    private static final String SYNC_STATE_FILE_NAME = "sync_state.json";
    private static final String INBOX_FILE_NAME = "inbox_messages.json";

    private final File storageDir;
    private final File licenseFile;
    private final File syncStateFile;
    private final File inboxFile;
    private final ObjectMapper mapper;

    public LicenseStorage() {
        this(LicensingConfig.getStorageDirectory());
    }

    public LicenseStorage(File storageDir) {
        this.storageDir = storageDir;
        if (!this.storageDir.exists()) {
            this.storageDir.mkdirs();
        }
        this.licenseFile = new File(storageDir, LICENSE_FILE_NAME);
        this.syncStateFile = new File(storageDir, SYNC_STATE_FILE_NAME);
        this.inboxFile = new File(storageDir, INBOX_FILE_NAME);

        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Loads the locally persisted license payload.
     * Returns null if missing or unreadable.
     */
    public synchronized LicensePayload loadLocalLicense() {
        if (!licenseFile.exists() || !licenseFile.isFile()) {
            return null;
        }
        try {
            return mapper.readValue(licenseFile, LicensePayload.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Saves the license payload to disk.
     */
    public synchronized void saveLocalLicense(LicensePayload license) throws IOException {
        if (license != null) {
            mapper.writeValue(licenseFile, license);
        }
    }

    /**
     * Reads the highest authenticated revision seen so far.
     */
    public synchronized int getHighestRevision() {
        Map<String, Object> state = loadSyncState();
        Object rev = state.get("highestRevision");
        if (rev instanceof Number) {
            return ((Number) rev).intValue();
        }
        return 0;
    }

    /**
     * Reads the license ID associated with the recorded revision.
     */
    public synchronized String getRecordedLicenseId() {
        Map<String, Object> state = loadSyncState();
        Object id = state.get("licenseId");
        return id != null ? id.toString() : null;
    }

    /**
     * Gets the last successful authenticated check date (YYYY-MM-DD).
     */
    public synchronized String getLastSuccessfulCheckDate() {
        Map<String, Object> state = loadSyncState();
        Object date = state.get("lastSuccessfulCheckDate");
        return date != null ? date.toString() : null;
    }

    /**
     * Updates the sync state after a successful authenticated server check.
     */
    public synchronized void updateSyncState(String lastCheckDate, int revision) {
        updateSyncState(lastCheckDate, revision, null);
    }

    /**
     * Updates the sync state and associates highestRevision with the specific licenseId.
     */
    public synchronized void updateSyncState(String lastCheckDate, int revision, String licenseId) {
        Map<String, Object> state = loadSyncState();
        if (lastCheckDate != null) {
            state.put("lastSuccessfulCheckDate", lastCheckDate);
        }
        String currentLicenseId = getRecordedLicenseId();
        boolean isNewLicenseId = (licenseId != null && !licenseId.equals(currentLicenseId));
        int currentHighest = getHighestRevision();
        if (isNewLicenseId || revision > currentHighest) {
            state.put("highestRevision", revision);
        }
        if (licenseId != null) {
            state.put("licenseId", licenseId);
        }
        saveSyncState(state);
    }

    /**
     * Loads saved customer inbox messages.
     */
    public synchronized List<CustomerMessage> loadInboxMessages() {
        if (!inboxFile.exists() || !inboxFile.isFile()) {
            return new ArrayList<>();
        }
        try {
            CustomerMessageEnvelope env = mapper.readValue(inboxFile, CustomerMessageEnvelope.class);
            return env.getMessages() != null ? env.getMessages() : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Saves updated inbox messages.
     */
    public synchronized void saveInboxMessages(String machineId, List<CustomerMessage> messages) {
        try {
            CustomerMessageEnvelope env = new CustomerMessageEnvelope(machineId, messages);
            mapper.writeValue(inboxFile, env);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadSyncState() {
        if (!syncStateFile.exists()) {
            return new ConcurrentHashMap<>();
        }
        try {
            return mapper.readValue(syncStateFile, Map.class);
        } catch (Exception e) {
            return new ConcurrentHashMap<>();
        }
    }

    private void saveSyncState(Map<String, Object> state) {
        try {
            mapper.writeValue(syncStateFile, state);
        } catch (Exception ignored) {
        }
    }

    public File getLicenseFile() {
        return licenseFile;
    }
}
