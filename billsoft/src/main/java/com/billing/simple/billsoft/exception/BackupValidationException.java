package com.billing.simple.billsoft.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BackupValidationException extends RuntimeException {
    private final String errorCode;
    private final List<String> details;

    public BackupValidationException(String message) {
        super(message);
        this.errorCode = "BACKUP_VALIDATION_ERROR";
        this.details = Collections.emptyList();
    }

    public BackupValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode != null ? errorCode : "BACKUP_VALIDATION_ERROR";
        this.details = Collections.emptyList();
    }

    public BackupValidationException(String errorCode, String message, List<String> details) {
        super(message);
        this.errorCode = errorCode != null ? errorCode : "BACKUP_VALIDATION_ERROR";
        this.details = details != null ? new ArrayList<>(details) : Collections.emptyList();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public List<String> getDetails() {
        return Collections.unmodifiableList(details);
    }
}
