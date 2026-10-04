package com.billing.simple.billsoft.exception;

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
