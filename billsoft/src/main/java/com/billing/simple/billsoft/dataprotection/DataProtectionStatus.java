package com.billing.simple.billsoft.dataprotection;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataProtectionStatus {

    private Instant lastSuccessfulCloudBackupAt;
    private long lastBackupSizeBytes;
    private String lastBlobSha;
    private Instant lastAttemptAt;
    private String lastError;

    public DataProtectionStatus() {
    }

    public Instant getLastSuccessfulCloudBackupAt() {
        return lastSuccessfulCloudBackupAt;
    }

    public void setLastSuccessfulCloudBackupAt(Instant lastSuccessfulCloudBackupAt) {
        this.lastSuccessfulCloudBackupAt = lastSuccessfulCloudBackupAt;
    }

    public long getLastBackupSizeBytes() {
        return lastBackupSizeBytes;
    }

    public void setLastBackupSizeBytes(long lastBackupSizeBytes) {
        this.lastBackupSizeBytes = lastBackupSizeBytes;
    }

    public String getLastBlobSha() {
        return lastBlobSha;
    }

    public void setLastBlobSha(String lastBlobSha) {
        this.lastBlobSha = lastBlobSha;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
