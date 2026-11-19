package com.billing.simple.billsoft.service.scheduler;

import java.time.Instant;

/**
 * Result returned from executing a Git-based check handler.
 */
public class GitCheckExecutionResult {

    private final boolean success;
    private final boolean notModified;
    private final String etag;
    private final String resultStatus; // "SUCCESS", "NOT_MODIFIED", "NETWORK_ERROR", "RATE_LIMITED", etc.
    private final String errorMessage;
    private final Instant rateLimitResetAt;

    public GitCheckExecutionResult(boolean success, boolean notModified, String etag, String resultStatus, String errorMessage, Instant rateLimitResetAt) {
        this.success = success;
        this.notModified = notModified;
        this.etag = etag;
        this.resultStatus = resultStatus;
        this.errorMessage = errorMessage;
        this.rateLimitResetAt = rateLimitResetAt;
    }

    public static GitCheckExecutionResult success(String etag) {
        return new GitCheckExecutionResult(true, false, etag, "SUCCESS", null, null);
    }

    public static GitCheckExecutionResult notModified(String etag) {
        return new GitCheckExecutionResult(true, true, etag, "NOT_MODIFIED", null, null);
    }

    public static GitCheckExecutionResult networkError(String errorMessage) {
        return new GitCheckExecutionResult(false, false, null, "NETWORK_ERROR", errorMessage, null);
    }

    public static GitCheckExecutionResult rateLimited(String errorMessage, Instant resetAt) {
        return new GitCheckExecutionResult(false, false, null, "RATE_LIMITED", errorMessage, resetAt);
    }

    public static GitCheckExecutionResult failure(String errorMessage) {
        return new GitCheckExecutionResult(false, false, null, "FAILURE", errorMessage, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isNotModified() {
        return notModified;
    }

    public String getEtag() {
        return etag;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getRateLimitResetAt() {
        return rateLimitResetAt;
    }
}

