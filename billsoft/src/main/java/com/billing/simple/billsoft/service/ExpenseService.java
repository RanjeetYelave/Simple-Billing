package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.PageResponse;
import com.billing.simple.billsoft.entities.Expense;
import com.billing.simple.billsoft.repo.ExpenseRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import java.util.*;

@Service
public class ExpenseService {
    private final ExpenseRepository repository;

    public ExpenseService(ExpenseRepository repository) {
        this.repository = repository;
    }

    public List<Expense> getExpensesByFirm(Long firmId) {
        Long target = firmId != null ? firmId : TenantContext.getCurrentFirmId();
        if (target == null) return Collections.emptyList();
        return repository.findByFirmIdOrderByExpenseDateDescIdDesc(target);
    }

    public PageResponse<Expense> getPaginatedExpenses(Long firmId, LocalDate from, LocalDate to, Pageable pageable) {
        Long target = firmId != null ? firmId : TenantContext.getCurrentFirmId();
        if (target == null) return PageResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
        Page<Expense> page;
        if (from != null && to != null) {
            page = repository.findByFirmIdAndExpenseDateBetween(target, from, to, pageable);
        } else {
            page = repository.findByFirmId(target, pageable);
        }
        return PageResponse.of(page);
    }

    public Expense getExpenseById(Long id, Long firmId) {
        Long target = firmId != null ? firmId : TenantContext.getCurrentFirmId();
        return (target != null ? repository.findByIdAndFirmId(id, target) : repository.findById(id)).orElse(null);
    }

    public Expense createExpense(Expense expense) {
        Long firmId = expense.getFirmId() != null ? expense.getFirmId() : TenantContext.getCurrentFirmId();
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
        Long firmId = TenantContext.getCurrentFirmId();
        Expense existing = (firmId != null ? repository.findByIdAndFirmId(id, firmId) : repository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));
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
        Long firmId = TenantContext.getCurrentFirmId();
        if (firmId != null) {
            if (!repository.existsByIdAndFirmId(id, firmId))
                return false;
            repository.deleteByIdAndFirmId(id, firmId);
            return true;
        } else {
            if (!repository.existsById(id))
                return false;
            repository.deleteById(id);
            return true;
        }
    }

    public static final List<String> DEFAULT_CATEGORIES = List.of(
            "Office Supplies",
            "Rent & Facilities",
            "Utilities (Electricity/Water/Internet)",
            "Salaries & Wages",
            "Travel & Conveyance",
            "Software & Digital Tools",
            "Marketing & Advertising",
            "Repairs & Maintenance",
            "Packaging & Shipping",
            "Legal & Professional Fees",
            "Taxes & Government Dues",
            "Miscellaneous");

    public List<String> getCategoriesByFirm(Long firmId) {
        Long target = firmId != null ? firmId : TenantContext.getCurrentFirmId();
        if (target == null) return new ArrayList<>(DEFAULT_CATEGORIES);
        Set<String> categories = new LinkedHashSet<>(DEFAULT_CATEGORIES);
        List<Expense> list = repository.findByFirmIdOrderByExpenseDateDescIdDesc(target);
        for (Expense e : list) {
            if (e.getCategory() != null && !e.getCategory().trim().isBlank()) {
                categories.add(e.getCategory().trim());
            }
        }
        return new ArrayList<>(categories);
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

        Map<String, BigDecimal> categoryTotals = new LinkedHashMap<>();
        for (Expense e : expenses) {
            if (e.getCategory() != null && !e.getCategory().trim().isEmpty()) {
                String cat = e.getCategory().trim();
                BigDecimal amt = e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO;
                categoryTotals.put(cat,
                        categoryTotals.getOrDefault(cat, BigDecimal.ZERO).add(amt).setScale(2, RoundingMode.HALF_UP));
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
