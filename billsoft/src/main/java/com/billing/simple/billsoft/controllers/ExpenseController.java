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
    public List<Expense> listByFirm(@RequestParam Long firmId) {
        return service.getExpensesByFirm(firmId);
    }

    @GetMapping("/firm/{firmId}")
    public List<Expense> listByFirmPath(@PathVariable Long firmId) {
        return service.getExpensesByFirm(firmId);
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
    public Map<String, Object> summary(@RequestParam Long firmId) {
        return service.getSummaryByFirm(firmId);
    }
}
