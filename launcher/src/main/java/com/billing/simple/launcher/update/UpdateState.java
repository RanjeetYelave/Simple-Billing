package com.billing.simple.launcher.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Durable update transaction journal persisted across processes and system restarts.
 * Guarantees crash-safe, atomic updates via temporary file promotion.
 */
public final class UpdateState {

    // Canonical 20-state update machine
    public static final String STATUS_IDLE = "IDLE";
    public static final String STATUS_UPDATE_AVAILABLE = "UPDATE_AVAILABLE";
    public static final String STATUS_PREPARING_DOWNLOAD = "PREPARING_DOWNLOAD";
    public static final String STATUS_DOWNLOADING = "DOWNLOADING";
    public static final String STATUS_DOWNLOAD_PAUSED = "DOWNLOAD_PAUSED";
    public static final String STATUS_DOWNLOAD_RETRYING = "DOWNLOAD_RETRYING";
    public static final String STATUS_DOWNLOAD_VERIFYING = "DOWNLOAD_VERIFYING";
    public static final String STATUS_DOWNLOAD_COMPLETED = "DOWNLOAD_COMPLETED";
    public static final String STATUS_INSTALLATION_PENDING = "INSTALLATION_PENDING";
    public static final String STATUS_BACKUP_IN_PROGRESS = "BACKUP_IN_PROGRESS";
    public static final String STATUS_READY_TO_INSTALL = "READY_TO_INSTALL";
    public static final String STATUS_APPLICATION_SHUTDOWN_PENDING = "APPLICATION_SHUTDOWN_PENDING";
    public static final String STATUS_APPLYING_UPDATE = "APPLYING_UPDATE";
    public static final String STATUS_APPLICATION_RESTARTING = "APPLICATION_RESTARTING";
    public static final String STATUS_POST_UPDATE_HEALTH_CHECK = "POST_UPDATE_HEALTH_CHECK";
    public static final String STATUS_UPDATE_SUCCESS = "UPDATE_SUCCESS";
    public static final String STATUS_ROLLBACK_IN_PROGRESS = "ROLLBACK_IN_PROGRESS";
    public static final String STATUS_ROLLBACK_COMPLETED = "ROLLBACK_COMPLETED";
    public static final String STATUS_UPDATE_FAILED = "UPDATE_FAILED";
    public static final String STATUS_RECOVERY_REQUIRED = "RECOVERY_REQUIRED";

    // Legacy & granular aliases (preserved for backward compatibility)
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_CHECKING = "CHECKING";
    public static final String STATUS_DOWNLOAD_CANCELLED = "DOWNLOAD_CANCELLED";
    public static final String STATUS_DOWNLOAD_INTERRUPTED = "DOWNLOAD_INTERRUPTED";
    public static final String STATUS_DOWNLOAD_FAILED = "DOWNLOAD_FAILED";
    public static final String STATUS_VERIFYING = "VERIFYING";
    public static final String STATUS_VERIFICATION_FAILED = "VERIFICATION_FAILED";
    public static final String STATUS_EXTRACTING = "EXTRACTING";
    public static final String STATUS_MIGRATING = "MIGRATING";
    public static final String STATUS_VERIFIED_STAGED = "VERIFIED_STAGED";
    public static final String STATUS_HANDOFF_REQUESTED = "HANDOFF_REQUESTED";
    public static final String STATUS_ACTIVATION_STARTED = "ACTIVATION_STARTED";
    public static final String STATUS_INSTALLING = "INSTALLING";
    public static final String STATUS_INSTALLATION_FAILED = "INSTALLATION_FAILED";
    public static final String STATUS_STARTING_NEW_VERSION = "STARTING_NEW_VERSION";
    public static final String STATUS_RESTARTING = "RESTARTING";
    public static final String STATUS_RESTART_FAILED = "RESTART_FAILED";
    public static final String STATUS_HEALTH_CHECK = "HEALTH_CHECK";
    public static final String STATUS_HEALTH_VERIFIED = "HEALTH_VERIFIED";
    public static final String STATUS_VERIFYING_INSTALLED_VERSION = "VERIFYING_INSTALLED_VERSION";
    public static final String STATUS_VERSION_MISMATCH = "VERSION_MISMATCH";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_COMMITTED = "COMMITTED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_ROLLED_BACK = "ROLLED_BACK";

    private String transactionId;
    private String status;
    private String currentVersion;
    private String targetVersion;
    private String expectedSha256;
    private long expectedSizeBytes;
    private long bytesDownloaded;
    private String etag;
    private String lastModified;
    private String downloadUrl;
    private String errorCategory;
    private int retryCount;
    private long nextRetryTime;
    private String stagedArtifactPath;
    private String backupArtifactPath;
    private String quarantineBackupPath;
    private String errorMessage;
    private long updatedAt;

    public UpdateState() {
        this.transactionId = UUID.randomUUID().toString();
        this.status = STATUS_IDLE;
        this.updatedAt = System.currentTimeMillis();
    }

    public UpdateState(String transactionId, String status, String currentVersion, String targetVersion,
                       String expectedSha256, long expectedSizeBytes, String stagedArtifactPath,
                       String backupArtifactPath, String quarantineBackupPath, String errorMessage,
                       long updatedAt) {
        this.transactionId = (transactionId != null && !transactionId.trim().isEmpty()) ? transactionId : UUID.randomUUID().toString();
        this.status = (status != null && !status.trim().isEmpty()) ? status : STATUS_IDLE;
        this.currentVersion = currentVersion != null ? currentVersion : "";
        this.targetVersion = targetVersion != null ? targetVersion : "";
        this.expectedSha256 = expectedSha256 != null ? expectedSha256 : "";
        this.expectedSizeBytes = expectedSizeBytes;
        this.bytesDownloaded = 0L;
        this.etag = "";
        this.lastModified = "";
        this.downloadUrl = "";
        this.errorCategory = "";
        this.retryCount = 0;
        this.nextRetryTime = 0L;
        this.stagedArtifactPath = stagedArtifactPath != null ? stagedArtifactPath : "";
        this.backupArtifactPath = backupArtifactPath != null ? backupArtifactPath : "";
        this.quarantineBackupPath = quarantineBackupPath != null ? quarantineBackupPath : "";
        this.errorMessage = errorMessage != null ? errorMessage : "";
        this.updatedAt = updatedAt > 0 ? updatedAt : System.currentTimeMillis();
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String id) { this.transactionId = id; }

    public String getOperationId() { return transactionId; }
    public void setOperationId(String id) { this.transactionId = id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = System.currentTimeMillis(); }

    public String getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }

    public String getTargetVersion() { return targetVersion; }
    public void setTargetVersion(String targetVersion) { this.targetVersion = targetVersion; }

    public String getExpectedSha256() { return expectedSha256; }
    public void setExpectedSha256(String expectedSha256) { this.expectedSha256 = expectedSha256; }

    public long getExpectedSizeBytes() { return expectedSizeBytes; }
    public void setExpectedSizeBytes(long expectedSizeBytes) { this.expectedSizeBytes = expectedSizeBytes; }

    public long getBytesDownloaded() { return bytesDownloaded; }
    public void setBytesDownloaded(long bytesDownloaded) { this.bytesDownloaded = bytesDownloaded; }

    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }

    public String getLastModified() { return lastModified; }
    public void setLastModified(String lastModified) { this.lastModified = lastModified; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getErrorCategory() { return errorCategory; }
    public void setErrorCategory(String errorCategory) { this.errorCategory = errorCategory; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public long getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(long nextRetryTime) { this.nextRetryTime = nextRetryTime; }

    public String getStagedArtifactPath() { return stagedArtifactPath; }
    public void setStagedArtifactPath(String stagedArtifactPath) { this.stagedArtifactPath = stagedArtifactPath; }

    public String getBackupArtifactPath() { return backupArtifactPath; }
    public void setBackupArtifactPath(String backupArtifactPath) { this.backupArtifactPath = backupArtifactPath; }

    public String getQuarantineBackupPath() { return quarantineBackupPath; }
    public void setQuarantineBackupPath(String quarantineBackupPath) { this.quarantineBackupPath = quarantineBackupPath; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public String toJson() {
        return "{\n" +
                "  \"transactionId\": \"" + escapeJson(transactionId) + "\",\n" +
                "  \"operationId\": \"" + escapeJson(transactionId) + "\",\n" +
                "  \"status\": \"" + escapeJson(status) + "\",\n" +
                "  \"currentVersion\": \"" + escapeJson(currentVersion) + "\",\n" +
                "  \"targetVersion\": \"" + escapeJson(targetVersion) + "\",\n" +
                "  \"expectedSha256\": \"" + escapeJson(expectedSha256) + "\",\n" +
                "  \"expectedSizeBytes\": " + expectedSizeBytes + ",\n" +
                "  \"bytesDownloaded\": " + bytesDownloaded + ",\n" +
                "  \"etag\": \"" + escapeJson(etag) + "\",\n" +
                "  \"lastModified\": \"" + escapeJson(lastModified) + "\",\n" +
                "  \"downloadUrl\": \"" + escapeJson(downloadUrl) + "\",\n" +
                "  \"errorCategory\": \"" + escapeJson(errorCategory) + "\",\n" +
                "  \"retryCount\": " + retryCount + ",\n" +
                "  \"nextRetryTime\": " + nextRetryTime + ",\n" +
                "  \"stagedArtifactPath\": \"" + escapeJson(stagedArtifactPath) + "\",\n" +
                "  \"backupArtifactPath\": \"" + escapeJson(backupArtifactPath) + "\",\n" +
                "  \"quarantineBackupPath\": \"" + escapeJson(quarantineBackupPath) + "\",\n" +
                "  \"errorMessage\": \"" + escapeJson(errorMessage) + "\",\n" +
                "  \"updatedAt\": " + updatedAt + "\n" +
                "}\n";
    }

    public static UpdateState fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new UpdateState();
        }
        String txId = extractString(json, "transactionId");
        if (txId.isEmpty()) {
            txId = extractString(json, "operationId");
        }
        UpdateState state = new UpdateState(
                txId,
                extractString(json, "status"),
                extractString(json, "currentVersion"),
                extractString(json, "targetVersion"),
                extractString(json, "expectedSha256"),
                extractLong(json, "expectedSizeBytes", 0L),
                extractString(json, "stagedArtifactPath"),
                extractString(json, "backupArtifactPath"),
                extractString(json, "quarantineBackupPath"),
                extractString(json, "errorMessage"),
                extractLong(json, "updatedAt", 0L)
        );
        state.setBytesDownloaded(extractLong(json, "bytesDownloaded", 0L));
        state.setEtag(extractString(json, "etag"));
        state.setLastModified(extractString(json, "lastModified"));
        state.setDownloadUrl(extractString(json, "downloadUrl"));
        state.setErrorCategory(extractString(json, "errorCategory"));
        state.setRetryCount((int) extractLong(json, "retryCount", 0L));
        state.setNextRetryTime(extractLong(json, "nextRetryTime", 0L));
        return state;
    }

    /**
     * Atomically saves this state to disk using a temporary file and atomic rename.
     */
    public synchronized void saveAtomic(Path targetFile) throws IOException {
        Path parent = targetFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Path tempFile = targetFile.resolveSibling(targetFile.getFileName().toString() + ".tmp." + UUID.randomUUID());
        byte[] bytes = toJson().getBytes(StandardCharsets.UTF_8);
        Files.write(tempFile, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            // Fallback for filesystems that do not support ATOMIC_MOVE
            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Loads the state from disk, or returns a new IDLE state if not found or corrupted.
     */
    public static UpdateState load(Path stateFile) {
        if (stateFile == null || !Files.exists(stateFile)) {
            return new UpdateState();
        }
        try {
            String json = Files.readString(stateFile, StandardCharsets.UTF_8);
            return fromJson(json);
        } catch (Exception e) {
            System.err.println("Failed to read update state file, reverting to IDLE: " + e.getMessage());
            return new UpdateState();
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r");
    }

    private static String extractString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return "";
    }

    private static long extractLong(String json, String key, long defaultValue) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
