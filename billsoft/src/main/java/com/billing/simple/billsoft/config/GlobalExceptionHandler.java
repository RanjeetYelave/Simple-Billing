package com.billing.simple.billsoft.config;

import com.billing.simple.billsoft.security.TenantSecurityException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxSizeException(MaxUploadSizeExceededException exc) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "The uploaded file exceeds the allowed maximum size limit (100MB).");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    @ExceptionHandler(com.billing.simple.billsoft.exception.InsufficientStockException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientStockException(com.billing.simple.billsoft.exception.InsufficientStockException exc) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", exc.getMessage());
        if (exc.getProductId() != null) response.put("productId", exc.getProductId());
        if (exc.getProductName() != null) response.put("productName", exc.getProductName());
        if (exc.getAvailableStock() != null) response.put("availableStock", exc.getAvailableStock());
        if (exc.getRequestedQuantity() != null) response.put("requestedQuantity", exc.getRequestedQuantity());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(TenantSecurityException.class)
    public ResponseEntity<Map<String, String>> handleTenantSecurityException(TenantSecurityException exc) {
        Map<String, String> response = new HashMap<>();
        response.put("error", exc.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException exc) {
        Map<String, String> response = new HashMap<>();
        response.put("error", exc.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException exc) {
        Map<String, String> response = new HashMap<>();
        response.put("error", exc.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler({
            org.springframework.orm.ObjectOptimisticLockingFailureException.class,
            jakarta.persistence.OptimisticLockException.class
    })
    public ResponseEntity<Map<String, String>> handleOptimisticLockException(Exception exc) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Record was modified by another operation. Please reload and try again.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(com.billing.simple.billsoft.exception.RestoreConflictException.class)
    public ResponseEntity<Map<String, Object>> handleRestoreConflictException(
            com.billing.simple.billsoft.exception.RestoreConflictException exc) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("status", "conflict");
        response.put("error", exc.getMessage());
        if (exc.getEntityType() != null) response.put("entityType", exc.getEntityType());
        if (exc.getConflictKey() != null) response.put("conflictKey", exc.getConflictKey());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
}

