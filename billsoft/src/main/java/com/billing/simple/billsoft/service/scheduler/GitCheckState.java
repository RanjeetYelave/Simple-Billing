package com.billing.simple.billsoft.service.scheduler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Durable state tracked per check type in git_scheduler_state.json.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitCheckState {

    private CheckType type;
    private Instant lastAttemptAt;
    private Instant lastSuccessAt;
    private Instant nextScheduledAt;
    private String etag;
    private String lastResult; // e.g. "SUCCESS", "NOT_MODIFIED", "FAILURE", "RATE_LIMITED"
    private int consecutiveFailures;
    private String lastErrorMessage;

    public GitCheckState() {
    }

    public GitCheckState(CheckType type) {
        this.type = type;
    }

    public CheckType getType() {
        return type;
    }

    public void setType(CheckType type) {
        this.type = type;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(Instant lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public Instant getNextScheduledAt() {
        return nextScheduledAt;
    }

    public void setNextScheduledAt(Instant nextScheduledAt) {
        this.nextScheduledAt = nextScheduledAt;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public String getLastResult() {
        return lastResult;
    }

    public void setLastResult(String lastResult) {
        this.lastResult = lastResult;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public boolean isDue(Instant now) {
        if (now == null) {
            now = Instant.now();
        }
        if (nextScheduledAt == null) {
            return true; // Never checked or schedule unset: check is due immediately
        }
        return !now.isBefore(nextScheduledAt);
    }
}

