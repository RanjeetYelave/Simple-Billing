package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.Expense;
import com.billing.simple.billsoft.service.ExpenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {
    private final ExpenseService service;
    private final com.billing.simple.billsoft.service.PlannerPdfService plannerPdfService;
    private final com.billing.simple.billsoft.repo.FirmDetailsRepository firmRepo;

    public ExpenseController(
            ExpenseService service,
            com.billing.simple.billsoft.service.PlannerPdfService plannerPdfService,
            com.billing.simple.billsoft.repo.FirmDetailsRepository firmRepo) {
        this.service = service;
        this.plannerPdfService = plannerPdfService;
        this.firmRepo = firmRepo;
    }

    @GetMapping
    public ResponseEntity<?> listByFirm(
            @RequestParam(required = false) Long firmId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && firmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm access prohibited");
        }
        Long target = authoritativeFirmId != null ? authoritativeFirmId : firmId;
        if (page != null || size != null) {
            org.springframework.data.domain.Pageable pageable = com.billing.simple.billsoft.util.PaginationUtils.createDefaultTransactionPageRequest(
                    page != null ? page : 0, size != null ? size : 25, "expenseDate");
            return ResponseEntity.ok(service.getPaginatedExpenses(target, from, to, pageable));
        }
        return ResponseEntity.ok(service.getExpensesByFirm(target));
    }

    @GetMapping("/firm/{firmId}")
    public List<Expense> listByFirmPath(@PathVariable Long firmId) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId == null) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Active firm context is required");
        }
        if (!authoritativeFirmId.equals(firmId)) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm access prohibited");
        }
        return service.getExpensesByFirm(authoritativeFirmId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Expense> getById(@PathVariable Long id) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        Expense e = service.getExpenseById(id, authoritativeFirmId);
        return e != null ? ResponseEntity.ok(e) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public Expense create(@RequestBody Expense expense) {
        return service.createExpense(expense);
    }

    @PutMapping("/{id}")
    public Expense update(@PathVariable Long id, @RequestBody Expense expense) {
        return service.updateExpense(id, expense);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = service.deleteExpense(id);
        return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(required = false) Long firmId) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && firmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm access prohibited");
        }
        Long target = authoritativeFirmId != null ? authoritativeFirmId : firmId;
        return service.getSummaryByFirm(target);
    }

    @GetMapping("/categories")
    public List<String> categories(@RequestParam(required = false) Long firmId) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && firmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm access prohibited");
        }
        Long target = authoritativeFirmId != null ? authoritativeFirmId : firmId;
        return service.getCategoriesByFirm(target);
    }

    @GetMapping("/statement/pdf")
    public ResponseEntity<byte[]> statementPdf(
            @RequestParam(required = false) Long firmId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String theme,
            @RequestParam(required = false) String color) throws Exception {

        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && firmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm access prohibited");
        }
        Long target = authoritativeFirmId != null ? authoritativeFirmId : firmId;
        List<Expense> expenses = service.getExpensesByFirm(target);
        if (from != null || to != null || (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category))) {
            expenses = expenses.stream()
                    .filter(e -> e.getExpenseDate() != null)
                    .filter(e -> from == null || !e.getExpenseDate().isBefore(from))
                    .filter(e -> to == null || !e.getExpenseDate().isAfter(to))
                    .filter(e -> category == null || category.isBlank() || "ALL".equalsIgnoreCase(category) || (e.getCategory() != null && e.getCategory().equalsIgnoreCase(category)))
                    .collect(java.util.stream.Collectors.toList());
        }

        com.billing.simple.billsoft.entities.FirmDetails firm = target != null ? firmRepo.findById(target).orElse(null) : null;
        byte[] pdf = plannerPdfService.generateExpenseStatementPdf(expenses, from, to, category, firm, null, format, theme, color);

        String firmName = (firm != null && firm.getFirmName() != null && !firm.getFirmName().trim().isEmpty())
                ? firm.getFirmName().trim().replaceAll("[^a-zA-Z0-9_-]", "_")
                : "Expense_Statement";
        String dateStr = (from != null || to != null)
                ? (from != null ? from.toString() : "Start") + "_to_" + (to != null ? to.toString() : java.time.LocalDate.now().toString())
                : "All_Time";
        String filename = firmName + "_Expenses_" + dateStr + ".pdf";

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.add(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
    }
}

