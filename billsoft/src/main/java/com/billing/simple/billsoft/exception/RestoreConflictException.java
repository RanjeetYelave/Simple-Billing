package com.billing.simple.billsoft.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class RestoreConflictException extends RuntimeException {
    private final String entityType;
    private final String conflictKey;

    public RestoreConflictException(String message) {
        super(message);
        this.entityType = null;
        this.conflictKey = null;
    }

    public RestoreConflictException(String entityType, String conflictKey, String message) {
        super(message);
        this.entityType = entityType;
        this.conflictKey = conflictKey;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getConflictKey() {
        return conflictKey;
    }
}
