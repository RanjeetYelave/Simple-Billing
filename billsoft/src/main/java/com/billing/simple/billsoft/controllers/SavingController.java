package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.SavingRecord;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.SavingService;
import com.billing.simple.billsoft.util.PaginationUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/savings")
public class SavingController {
    private final SavingService service;

    public SavingController(SavingService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> listByFirm(
            @RequestParam(required = false) Long firmId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId == null) authoritativeFirmId = firmId;
        if (page != null || size != null) {
            org.springframework.data.domain.Pageable pageable = PaginationUtils.createDefaultTransactionPageRequest(
                    page != null ? page : 0, size != null ? size : 25, "savingDate");
            return ResponseEntity.ok(service.getPaginatedSavings(authoritativeFirmId, from, to, pageable));
        }
        return ResponseEntity.ok(service.getSavingsByFirm(authoritativeFirmId));
    }

    @GetMapping("/firm/{firmId}")
    public List<SavingRecord> listByFirmPath(@PathVariable Long firmId) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }
        return service.getSavingsByFirm(authoritativeFirmId != null ? authoritativeFirmId : firmId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SavingRecord> getById(@PathVariable Long id) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        SavingRecord s = service.getSavingById(id, authoritativeFirmId);
        return s != null ? ResponseEntity.ok(s) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public SavingRecord create(@RequestBody SavingRecord saving) {
        return service.createSaving(saving);
    }

    @PutMapping("/{id}")
    public SavingRecord update(@PathVariable Long id, @RequestBody SavingRecord saving) {
        return service.updateSaving(id, saving);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = service.deleteSaving(id);
        return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(required = false) Long firmId) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId == null) authoritativeFirmId = firmId;
        return service.getSummaryByFirm(authoritativeFirmId);
    }

    @GetMapping("/categories")
    public List<String> categories(@RequestParam(required = false) Long firmId) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId == null) authoritativeFirmId = firmId;
        return service.getCategoriesByFirm(authoritativeFirmId);
    }
}
