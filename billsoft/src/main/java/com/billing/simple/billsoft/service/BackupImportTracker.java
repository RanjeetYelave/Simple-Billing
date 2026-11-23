package com.billing.simple.billsoft.service;

import org.springframework.stereotype.Component;

@Component
public class BackupImportTracker {

    public static class ImportProgress {
        private String status = "IDLE"; // IDLE, VALIDATING, PROCESSING, COMMITTING, COMPLETED, FAILED
        private String message = "";
        private String currentStage = "";
        private int currentFirmIndex = 0;
        private int totalFirms = 0;
        private String currentFirmName = "";
        private int percent = 0;
        private long startTime = 0;
        private long updatedTime = 0;
        private String error = null;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getCurrentStage() { return currentStage; }
        public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }
        public int getCurrentFirmIndex() { return currentFirmIndex; }
        public void setCurrentFirmIndex(int currentFirmIndex) { this.currentFirmIndex = currentFirmIndex; }
        public int getTotalFirms() { return totalFirms; }
        public void setTotalFirms(int totalFirms) { this.totalFirms = totalFirms; }
        public String getCurrentFirmName() { return currentFirmName; }
        public void setCurrentFirmName(String currentFirmName) { this.currentFirmName = currentFirmName; }
        public int getPercent() { return percent; }
        public void setPercent(int percent) { this.percent = percent; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public long getUpdatedTime() { return updatedTime; }
        public void setUpdatedTime(long updatedTime) { this.updatedTime = updatedTime; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    private final ImportProgress globalProgress = new ImportProgress();

    public synchronized void start(int totalFirms, String initialMessage) {
        globalProgress.setStatus("PROCESSING");
        globalProgress.setTotalFirms(totalFirms);
        globalProgress.setCurrentFirmIndex(0);
        globalProgress.setCurrentFirmName("");
        globalProgress.setCurrentStage("Initializing");
        globalProgress.setPercent(5);
        globalProgress.setMessage(initialMessage != null ? initialMessage : "Starting restore...");
        globalProgress.setStartTime(System.currentTimeMillis());
        globalProgress.setUpdatedTime(System.currentTimeMillis());
        globalProgress.setError(null);
    }

    public synchronized void updateStage(String stage, int firmIndex, String firmName, int percent, String message) {
        globalProgress.setCurrentStage(stage);
        globalProgress.setCurrentFirmIndex(firmIndex);
        if (firmName != null && !firmName.isBlank()) {
            globalProgress.setCurrentFirmName(firmName);
        }
        globalProgress.setPercent(percent);
        if (message != null && !message.isBlank()) {
            globalProgress.setMessage(message);
        }
        globalProgress.setUpdatedTime(System.currentTimeMillis());
    }

    public synchronized void complete(String message) {
        globalProgress.setStatus("COMPLETED");
        globalProgress.setCurrentStage("Done");
        globalProgress.setPercent(100);
        globalProgress.setMessage(message != null ? message : "Restore completed successfully.");
        globalProgress.setUpdatedTime(System.currentTimeMillis());
    }

    public synchronized void fail(String errorMessage) {
        globalProgress.setStatus("FAILED");
        globalProgress.setCurrentStage("Error");
        globalProgress.setError(errorMessage);
        globalProgress.setMessage("Restore failed: " + (errorMessage != null ? errorMessage : "Unknown error"));
        globalProgress.setUpdatedTime(System.currentTimeMillis());
    }

    public synchronized ImportProgress getProgress() {
        ImportProgress copy = new ImportProgress();
        copy.setStatus(globalProgress.getStatus());
        copy.setMessage(globalProgress.getMessage());
        copy.setCurrentStage(globalProgress.getCurrentStage());
        copy.setCurrentFirmIndex(globalProgress.getCurrentFirmIndex());
        copy.setTotalFirms(globalProgress.getTotalFirms());
        copy.setCurrentFirmName(globalProgress.getCurrentFirmName());
        copy.setPercent(globalProgress.getPercent());
        copy.setStartTime(globalProgress.getStartTime());
        copy.setUpdatedTime(globalProgress.getUpdatedTime());
        copy.setError(globalProgress.getError());
        return copy;
    }
}
