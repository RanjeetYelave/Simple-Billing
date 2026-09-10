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

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> listByFirm(
            @RequestParam(required = false) Long firmId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId == null) authoritativeFirmId = firmId;
        if (page != null || size != null) {
            org.springframework.data.domain.Pageable pageable = com.billing.simple.billsoft.util.PaginationUtils.createDefaultTransactionPageRequest(
                    page != null ? page : 0, size != null ? size : 25, "expenseDate");
            return ResponseEntity.ok(service.getPaginatedExpenses(authoritativeFirmId, from, to, pageable));
        }
        return ResponseEntity.ok(service.getExpensesByFirm(authoritativeFirmId));
    }

    @GetMapping("/firm/{firmId}")
    public List<Expense> listByFirmPath(@PathVariable Long firmId) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm access prohibited");
        }
        return service.getExpensesByFirm(authoritativeFirmId != null ? authoritativeFirmId : firmId);
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
        if (authoritativeFirmId == null) authoritativeFirmId = firmId;
        return service.getSummaryByFirm(authoritativeFirmId);
    }
}
