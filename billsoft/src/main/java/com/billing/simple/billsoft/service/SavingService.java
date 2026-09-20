package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.PageResponse;
import com.billing.simple.billsoft.entities.SavingRecord;
import com.billing.simple.billsoft.repo.SavingRepository;
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
public class SavingService {
    private final SavingRepository repository;
    private final GoalService goalService;

    public static final List<String> DEFAULT_CATEGORIES = List.of(
            "Mutual Funds / SIP",
            "Gold & Silver",
            "Emergency Fund",
            "Fixed Deposit / RD",
            "Business Reserves",
            "Cash Savings",
            "Tax Provision",
            "Retirement Fund",
            "Stock Market",
            "Real Estate Fund");

    public SavingService(SavingRepository repository, GoalService goalService) {
        this.repository = repository;
        this.goalService = goalService;
    }

    public List<SavingRecord> getSavingsByFirm(Long firmId) {
        Long target = firmId != null ? firmId : TenantContext.getCurrentFirmId();
        if (target == null) return Collections.emptyList();
        return repository.findByFirmIdOrderBySavingDateDescIdDesc(target);
    }

    public PageResponse<SavingRecord> getPaginatedSavings(Long firmId, LocalDate from, LocalDate to,
            Pageable pageable) {
        Long target = firmId != null ? firmId : TenantContext.getCurrentFirmId();
        if (target == null) return PageResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
        Page<SavingRecord> page;
        if (from != null && to != null) {
            page = repository.findByFirmIdAndSavingDateBetween(target, from, to, pageable);
        } else {
            page = repository.findByFirmId(target, pageable);
        }
        return PageResponse.of(page);
    }

    public SavingRecord getSavingById(Long id, Long firmId) {
        Long target = firmId != null ? firmId : TenantContext.getCurrentFirmId();
        return (target != null ? repository.findByIdAndFirmId(id, target) : repository.findById(id)).orElse(null);
    }

    @Transactional
    public SavingRecord createSaving(SavingRecord saving) {
        Long firmId = saving.getFirmId() != null ? saving.getFirmId() : TenantContext.getCurrentFirmId();
        if (firmId != null) {
            saving.setFirmId(firmId);
        }
        if (saving.getSavingDate() == null) {
            saving.setSavingDate(LocalDate.now());
        }
        if (saving.getAmount() != null) {
            saving.setAmount(saving.getAmount().setScale(2, RoundingMode.HALF_UP));
        }

        // Auto-match goal if not explicitly provided but tag/category/title matches a goal
        if (saving.getGoalId() == null && firmId != null) {
            List<com.billing.simple.billsoft.entities.Goal> goals = goalService.getGoalsByFirm(firmId);
            for (com.billing.simple.billsoft.entities.Goal g : goals) {
                if (g.getGoalType() == com.billing.simple.billsoft.entities.GoalType.SAVINGS_TARGET) {
                    boolean titleMatch = saving.getTitle() != null
                            && saving.getTitle().toLowerCase().contains(g.getTitle().toLowerCase());
                    boolean catMatch = saving.getCategory() != null
                            && saving.getCategory().equalsIgnoreCase(g.getTitle());
                    boolean tagMatch = false;
                    if (g.getTags() != null && !g.getTags().isBlank()) {
                        String[] gTags = g.getTags().split(",");
                        for (String gt : gTags) {
                            String trimmed = gt.trim().toLowerCase();
                            if (!trimmed.isEmpty()) {
                                if (saving.getTags() != null && saving.getTags().toLowerCase().contains(trimmed))
                                    tagMatch = true;
                                if (saving.getTitle() != null && saving.getTitle().toLowerCase().contains(trimmed))
                                    tagMatch = true;
                                if (saving.getCategory() != null
                                        && saving.getCategory().toLowerCase().contains(trimmed))
                                    tagMatch = true;
                            }
                        }
                    }
                    if (catMatch || tagMatch || titleMatch) {
                        saving.setGoalId(g.getId());
                        break;
                    }
                }
            }
        }

        SavingRecord saved = repository.save(saving);

        if (saved.getGoalId() != null && saved.getFirmId() != null) {
            goalService.recalculateSavingsGoal(saved.getFirmId(), saved.getGoalId());
        }

        return saved;
    }

    @Transactional
    public SavingRecord updateSaving(Long id, SavingRecord updated) {
        Long firmId = TenantContext.getCurrentFirmId();
        SavingRecord existing = (firmId != null ? repository.findByIdAndFirmId(id, firmId) : repository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Saving record not found"));

        Long oldGoalId = existing.getGoalId();

        existing.setTitle(updated.getTitle());
        if (updated.getAmount() != null) {
            existing.setAmount(updated.getAmount().setScale(2, RoundingMode.HALF_UP));
        }
        existing.setCategory(updated.getCategory());
        existing.setSavingDate(updated.getSavingDate());
        existing.setPaymentMode(updated.getPaymentMode());
        existing.setGoalId(updated.getGoalId());
        existing.setNotes(updated.getNotes());
        existing.setTags(updated.getTags());

        Long effFirmId = existing.getFirmId() != null ? existing.getFirmId() : firmId;

        // Auto-match goal if not explicitly provided but tag/category matches
        if (existing.getGoalId() == null && effFirmId != null) {
            List<com.billing.simple.billsoft.entities.Goal> goals = goalService.getGoalsByFirm(effFirmId);
            for (com.billing.simple.billsoft.entities.Goal g : goals) {
                if (g.getGoalType() == com.billing.simple.billsoft.entities.GoalType.SAVINGS_TARGET) {
                    boolean titleMatch = existing.getTitle() != null
                            && existing.getTitle().toLowerCase().contains(g.getTitle().toLowerCase());
                    boolean catMatch = existing.getCategory() != null
                            && existing.getCategory().equalsIgnoreCase(g.getTitle());
                    boolean tagMatch = false;
                    if (g.getTags() != null && !g.getTags().isBlank()) {
                        String[] gTags = g.getTags().split(",");
                        for (String gt : gTags) {
                            String trimmed = gt.trim().toLowerCase();
                            if (!trimmed.isEmpty()) {
                                if (existing.getTags() != null && existing.getTags().toLowerCase().contains(trimmed))
                                    tagMatch = true;
                                if (existing.getTitle() != null && existing.getTitle().toLowerCase().contains(trimmed))
                                    tagMatch = true;
                                if (existing.getCategory() != null
                                        && existing.getCategory().toLowerCase().contains(trimmed))
                                    tagMatch = true;
                            }
                        }
                    }
                    if (catMatch || tagMatch || titleMatch) {
                        existing.setGoalId(g.getId());
                        break;
                    }
                }
            }
        }

        SavingRecord saved = repository.save(existing);

        if (oldGoalId != null && !oldGoalId.equals(saved.getGoalId()) && effFirmId != null) {
            goalService.recalculateSavingsGoal(effFirmId, oldGoalId);
        }
        if (saved.getGoalId() != null && effFirmId != null) {
            goalService.recalculateSavingsGoal(effFirmId, saved.getGoalId());
        }

        return saved;
    }

    @Transactional
    public boolean deleteSaving(Long id) {
        Long firmId = TenantContext.getCurrentFirmId();
        SavingRecord existing = (firmId != null ? repository.findByIdAndFirmId(id, firmId) : repository.findById(id)).orElse(null);

        if (existing == null)
            return false;

        Long linkedGoalId = existing.getGoalId();
        Long effFirmId = existing.getFirmId();

        if (firmId != null) {
            repository.deleteByIdAndFirmId(id, firmId);
        } else {
            repository.deleteById(id);
        }

        if (linkedGoalId != null && effFirmId != null) {
            goalService.recalculateSavingsGoal(effFirmId, linkedGoalId);
        }

        return true;
    }

    public List<String> getCategoriesByFirm(Long firmId) {
        Long target = firmId != null ? firmId : TenantContext.getCurrentFirmId();
        if (target == null) return new ArrayList<>(DEFAULT_CATEGORIES);
        Set<String> categories = new LinkedHashSet<>(DEFAULT_CATEGORIES);
        List<SavingRecord> list = repository.findByFirmIdOrderBySavingDateDescIdDesc(target);
        for (SavingRecord s : list) {
            if (s.getCategory() != null && !s.getCategory().trim().isBlank()) {
                categories.add(s.getCategory().trim());
            }
        }
        return new ArrayList<>(categories);
    }

    public Map<String, Object> getSummaryByFirm(Long firmId) {
        List<SavingRecord> savings = getSavingsByFirm(firmId);
        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();

        BigDecimal totalAmount = savings.stream()
                .map(s -> s.getAmount() != null ? s.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal currentMonthAmount = savings.stream()
                .filter(s -> s.getSavingDate() != null &&
                        s.getSavingDate().getMonthValue() == currentMonth &&
                        s.getSavingDate().getYear() == currentYear)
                .map(s -> s.getAmount() != null ? s.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> categoryTotals = new LinkedHashMap<>();
        for (SavingRecord s : savings) {
            if (s.getCategory() != null && !s.getCategory().trim().isEmpty()) {
                String cat = s.getCategory().trim();
                BigDecimal amt = s.getAmount() != null ? s.getAmount() : BigDecimal.ZERO;
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
        summary.put("count", savings.size());
        summary.put("byCategory", categoryTotals);

        return summary;
    }
}
