package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Expense;
import com.billing.simple.billsoft.repo.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public Expense createExpense(Expense expense) {
        if (expense.getExpenseDate() == null) {
            expense.setExpenseDate(LocalDate.now());
        }
        return repository.save(expense);
    }

    @Transactional
    public Expense updateExpense(Long id, Expense updated) {
        Expense existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));
        existing.setTitle(updated.getTitle());
        existing.setAmount(updated.getAmount());
        existing.setCategory(updated.getCategory());
        existing.setExpenseDate(updated.getExpenseDate());
        existing.setPaymentMode(updated.getPaymentMode());
        existing.setNotes(updated.getNotes());
        if (updated.getFirmId() != null) {
            existing.setFirmId(updated.getFirmId());
        }
        return repository.save(existing);
    }

    @Transactional
    public boolean deleteExpense(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }

    public Map<String, Object> getSummaryByFirm(Long firmId) {
        List<Expense> expenses = getExpensesByFirm(firmId);
        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();

        double totalAmount = expenses.stream().mapToDouble(Expense::getAmount).sum();
        double currentMonthAmount = expenses.stream()
                .filter(e -> e.getExpenseDate() != null &&
                        e.getExpenseDate().getMonthValue() == currentMonth &&
                        e.getExpenseDate().getYear() == currentYear)
                .mapToDouble(Expense::getAmount).sum();

        Map<String, Double> categoryTotals = expenses.stream()
                .filter(e -> e.getCategory() != null && !e.getCategory().isEmpty())
                .collect(Collectors.groupingBy(
                        Expense::getCategory,
                        Collectors.summingDouble(Expense::getAmount)
                ));

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
