package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.PageResponse;
import com.billing.simple.billsoft.entities.Expense;
import com.billing.simple.billsoft.repo.ExpenseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExpenseService {
    private final ExpenseRepository repository;

    public ExpenseService(ExpenseRepository repository) {
        this.repository = repository;
    }

    public List<Expense> getExpensesByFirm(Long firmId) {
        return repository.findByFirmIdOrderByExpenseDateDescIdDesc(firmId);
    }

    public PageResponse<Expense> getPaginatedExpenses(Long firmId, LocalDate from, LocalDate to, Pageable pageable) {
        if (firmId == null) {
            return PageResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
        }
        Page<Expense> page;
        if (from != null && to != null) {
            page = repository.findByFirmIdAndExpenseDateBetween(firmId, from, to, pageable);
        } else {
            page = repository.findByFirmId(firmId, pageable);
        }
        return PageResponse.of(page);
    }

    public Expense getExpenseById(Long id, Long firmId) {
        if (firmId != null) {
            return repository.findByIdAndFirmId(id, firmId).orElse(null);
        }
        return repository.findById(id).orElse(null);
    }

    public Expense createExpense(Expense expense) {
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (firmId != null) {
            expense.setFirmId(firmId);
        }
        if (expense.getExpenseDate() == null) {
            expense.setExpenseDate(LocalDate.now());
        }
        if (expense.getAmount() != null) {
            expense.setAmount(expense.getAmount().setScale(2, RoundingMode.HALF_UP));
        }
        return repository.save(expense);
    }

    @Transactional
    public Expense updateExpense(Long id, Expense updated) {
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        Expense existing = (firmId != null)
                ? repository.findByIdAndFirmId(id, firmId).orElseThrow(() -> new IllegalArgumentException("Expense not found"))
                : repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Expense not found"));
        existing.setTitle(updated.getTitle());
        if (updated.getAmount() != null) {
            existing.setAmount(updated.getAmount().setScale(2, RoundingMode.HALF_UP));
        }
        existing.setCategory(updated.getCategory());
        existing.setExpenseDate(updated.getExpenseDate());
        existing.setPaymentMode(updated.getPaymentMode());
        existing.setNotes(updated.getNotes());
        // Do NOT allow changing firmId on update
        return repository.save(existing);
    }

    @Transactional
    public boolean deleteExpense(Long id) {
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (firmId != null) {
            if (!repository.existsByIdAndFirmId(id, firmId)) return false;
            repository.deleteByIdAndFirmId(id, firmId);
            return true;
        }
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }

    public Map<String, Object> getSummaryByFirm(Long firmId) {
        List<Expense> expenses = getExpensesByFirm(firmId);
        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();

        BigDecimal totalAmount = expenses.stream()
                .map(e -> e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal currentMonthAmount = expenses.stream()
                .filter(e -> e.getExpenseDate() != null &&
                        e.getExpenseDate().getMonthValue() == currentMonth &&
                        e.getExpenseDate().getYear() == currentYear)
                .map(e -> e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> categoryTotals = new HashMap<>();
        for (Expense e : expenses) {
            if (e.getCategory() != null && !e.getCategory().trim().isEmpty()) {
                String cat = e.getCategory().trim();
                BigDecimal amt = e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO;
                categoryTotals.put(cat, categoryTotals.getOrDefault(cat, BigDecimal.ZERO).add(amt).setScale(2, RoundingMode.HALF_UP));
            }
        }

        String topCategory = categoryTotals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalAllTime", totalAmount);
        summary.put("totalCurrentMonth", currentMonthAmount);
        summary.put("topCategory", topCategory);
        summary.put("count", expenses.size());
        summary.put("byCategory", categoryTotals);

        return summary;
    }
}
