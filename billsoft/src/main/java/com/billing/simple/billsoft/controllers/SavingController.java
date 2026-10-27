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
    private final com.billing.simple.billsoft.service.PlannerPdfService plannerPdfService;
    private final com.billing.simple.billsoft.repo.FirmDetailsRepository firmRepo;

    public SavingController(
            SavingService service,
            com.billing.simple.billsoft.service.PlannerPdfService plannerPdfService,
            com.billing.simple.billsoft.repo.FirmDetailsRepository firmRepo) {
        this.service = service;
        this.plannerPdfService = plannerPdfService;
        this.firmRepo = firmRepo;
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

    @GetMapping("/ledger/pdf")
    public ResponseEntity<byte[]> ledgerPdf(
            @RequestParam(required = false) Long firmId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String theme,
            @RequestParam(required = false) String color) throws Exception {

        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && firmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }
        Long target = authoritativeFirmId != null ? authoritativeFirmId : firmId;
        List<SavingRecord> savings = service.getSavingsByFirm(target);
        if (from != null || to != null || (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category))) {
            savings = savings.stream()
                    .filter(s -> s.getSavingDate() != null)
                    .filter(s -> from == null || !s.getSavingDate().isBefore(from))
                    .filter(s -> to == null || !s.getSavingDate().isAfter(to))
                    .filter(s -> category == null || category.isBlank() || "ALL".equalsIgnoreCase(category) || (s.getCategory() != null && s.getCategory().equalsIgnoreCase(category)))
                    .collect(java.util.stream.Collectors.toList());
        }

        com.billing.simple.billsoft.entities.FirmDetails firm = target != null ? firmRepo.findById(target).orElse(null) : null;
        byte[] pdf = plannerPdfService.generateSavingsLedgerPdf(savings, from, to, category, firm, null, format, theme, color);

        String firmName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().trim().isEmpty())
                ? firm.getFirmName().trim().replaceAll("[^a-zA-Z0-9_-]", "_")
                : "Savings_Ledger";
        String dateStr = (from != null || to != null)
                ? (from != null ? from.toString() : "Start") + "_to_" + (to != null ? to.toString() : LocalDate.now().toString())
                : "All_Time";
        String filename = firmName + "_Savings_" + dateStr + ".pdf";

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.add(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
    }
}
