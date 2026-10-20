package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.dtos.migration.InvoiceMigrationDTOs.*;
import com.billing.simple.billsoft.service.InvoiceMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@CrossOrigin
@RequiredArgsConstructor
public class InvoiceMigrationController {

    private final InvoiceMigrationService migrationService;

    /**
     * Preview cross-firm migration for an invoice (Read-only check).
     */
    @GetMapping("/{id}/migration-preview")
    public ResponseEntity<InvoiceMigrationPreviewResponse> getMigrationPreview(
            @PathVariable Long id,
            @RequestParam Long targetFirmId) {
        return ResponseEntity.ok(migrationService.previewMigration(id, targetFirmId));
    }

    @PostMapping("/{id}/migration-preview")
    public ResponseEntity<InvoiceMigrationPreviewResponse> postMigrationPreview(
            @PathVariable Long id,
            @RequestParam Long targetFirmId) {
        return ResponseEntity.ok(migrationService.previewMigration(id, targetFirmId));
    }

    /**
     * Execute forward migration from invoice source firm to target firm.
     */
    @PostMapping("/{id}/migrate")
    public ResponseEntity<InvoiceMigrationResponse> migrateInvoice(
            @PathVariable Long id,
            @RequestBody InvoiceMigrationRequest request) {
        return ResponseEntity.ok(migrationService.migrateInvoice(id, request));
    }

    /**
     * Revert invoice to its exact pre-migration snapshot state in source firm.
     */
    @PostMapping("/{id}/revert-migration")
    public ResponseEntity<InvoiceMigrationResponse> revertMigration(
            @PathVariable Long id) {
        return ResponseEntity.ok(migrationService.revertMigration(id));
    }

    /**
     * Retrieve immutable cross-firm migration history for an invoice.
     */
    @GetMapping("/{id}/migration-history")
    public ResponseEntity<List<InvoiceMigrationRecordDTO>> getMigrationHistory(
            @PathVariable Long id) {
        return ResponseEntity.ok(migrationService.getMigrationHistory(id));
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<java.util.Map<String, Object>> handleNotFound(jakarta.persistence.EntityNotFoundException ex) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("error", ex.getMessage());
        body.put("status", 404);
        return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<java.util.Map<String, Object>> handleConflict(IllegalStateException ex) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("error", ex.getMessage());
        body.put("status", 409);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<java.util.Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("error", ex.getMessage());
        body.put("status", 400);
        return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(com.billing.simple.billsoft.security.TenantSecurityException.class)
    public ResponseEntity<java.util.Map<String, Object>> handleForbidden(com.billing.simple.billsoft.security.TenantSecurityException ex) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("error", ex.getMessage());
        body.put("status", 403);
        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(body);
    }
}
