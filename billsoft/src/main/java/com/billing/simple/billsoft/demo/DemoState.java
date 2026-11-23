package com.billing.simple.billsoft.demo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DemoState {
    private String activeMode = "NORMAL";
    private boolean cleanExit = true;
    private Instant lastEnteredAt;
    private Instant lastExitedAt;
    private Long activeDemoFirmId;
    private Long preservedNormalFirmId;
    private boolean crashRecovered;

    public DemoState() {}

    public String getActiveMode() {
        return activeMode;
    }

    public void setActiveMode(String activeMode) {
        this.activeMode = activeMode;
    }

    public boolean isCleanExit() {
        return cleanExit;
    }

    public void setCleanExit(boolean cleanExit) {
        this.cleanExit = cleanExit;
    }

    public Instant getLastEnteredAt() {
        return lastEnteredAt;
    }

    public void setLastEnteredAt(Instant lastEnteredAt) {
        this.lastEnteredAt = lastEnteredAt;
    }

    public Instant getLastExitedAt() {
        return lastExitedAt;
    }

    public void setLastExitedAt(Instant lastExitedAt) {
        this.lastExitedAt = lastExitedAt;
    }

    public Long getActiveDemoFirmId() {
        return activeDemoFirmId;
    }

    public void setActiveDemoFirmId(Long activeDemoFirmId) {
        this.activeDemoFirmId = activeDemoFirmId;
    }

    public Long getPreservedNormalFirmId() {
        return preservedNormalFirmId;
    }

    public void setPreservedNormalFirmId(Long preservedNormalFirmId) {
        this.preservedNormalFirmId = preservedNormalFirmId;
    }

    public boolean isCrashRecovered() {
        return crashRecovered;
    }

    public void setCrashRecovered(boolean crashRecovered) {
        this.crashRecovered = crashRecovered;
    }
}
