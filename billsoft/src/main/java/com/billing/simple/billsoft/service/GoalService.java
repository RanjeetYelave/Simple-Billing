package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Goal;
import com.billing.simple.billsoft.entities.GoalType;
import com.billing.simple.billsoft.entities.SavingRecord;
import com.billing.simple.billsoft.repo.GoalRepository;
import com.billing.simple.billsoft.repo.SavingRepository;
import com.billing.simple.billsoft.security.TenantContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class GoalService {
    private final GoalRepository goalRepository;
    private final SavingRepository savingRepository;

    public GoalService(GoalRepository goalRepository, @Lazy SavingRepository savingRepository) {
        this.goalRepository = goalRepository;
        this.savingRepository = savingRepository;
    }

    public List<Goal> getGoalsByFirm(Long firmId) {
        List<Goal> list = goalRepository.findByFirmIdOrderByCreatedAtDesc(firmId);
        // Auto-update QUIT_HABIT streak days dynamically if active
        LocalDate today = LocalDate.now();
        for (Goal g : list) {
            if (g.getGoalType() == GoalType.QUIT_HABIT && "ACTIVE".equalsIgnoreCase(g.getStatus()) && g.getStartDate() != null) {
                long days = ChronoUnit.DAYS.between(g.getStartDate(), today);
                if (days < 0) days = 0;
                g.setCurrentStreak((int) days);
                if (g.getLongestStreak() == null || days > g.getLongestStreak()) {
                    g.setLongestStreak((int) days);
                }
            }
        }
        return list;
    }

    public Goal getGoalById(Long id, Long firmId) {
        if (firmId != null) {
            return goalRepository.findByIdAndFirmId(id, firmId).orElse(null);
        }
        return goalRepository.findById(id).orElse(null);
    }

    @Transactional
    public Goal createGoal(Goal goal) {
        Long firmId = TenantContext.getCurrentFirmId();
        if (firmId != null) {
            goal.setFirmId(firmId);
        }
        if (goal.getStartDate() == null) {
            goal.setStartDate(LocalDate.now());
        }
        if (goal.getStatus() == null || goal.getStatus().isBlank()) {
            goal.setStatus("ACTIVE");
        }
        if (goal.getCurrentValue() == null) {
            goal.setCurrentValue(BigDecimal.ZERO);
        }
        if (goal.getTargetValue() != null) {
            goal.setTargetValue(goal.getTargetValue().setScale(2, RoundingMode.HALF_UP));
        }
        if (goal.getCurrentStreak() == null) {
            goal.setCurrentStreak(0);
        }
        if (goal.getLongestStreak() == null) {
            goal.setLongestStreak(0);
        }
        Goal saved = goalRepository.save(goal);
        if (saved.getGoalType() == GoalType.SAVINGS_TARGET && saved.getFirmId() != null) {
            recalculateSavingsGoal(saved.getFirmId(), saved.getId());
            saved = goalRepository.findById(saved.getId()).orElse(saved);
        }
        return saved;
    }

    @Transactional
    public Goal updateGoal(Long id, Goal updated) {
        Long firmId = TenantContext.getCurrentFirmId();
        Goal existing = (firmId != null)
                ? goalRepository.findByIdAndFirmId(id, firmId).orElseThrow(() -> new IllegalArgumentException("Goal not found"))
                : goalRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        existing.setTitle(updated.getTitle());
        if (updated.getGoalType() != null) {
            existing.setGoalType(updated.getGoalType());
        }
        if (updated.getTargetValue() != null) {
            existing.setTargetValue(updated.getTargetValue().setScale(2, RoundingMode.HALF_UP));
        }
        if (updated.getCurrentValue() != null) {
            existing.setCurrentValue(updated.getCurrentValue().setScale(2, RoundingMode.HALF_UP));
        }
        existing.setUnit(updated.getUnit());
        existing.setStartDate(updated.getStartDate());
        existing.setTargetDate(updated.getTargetDate());
        if (updated.getStatus() != null) {
            existing.setStatus(updated.getStatus());
        }
        existing.setIcon(updated.getIcon());
        existing.setColor(updated.getColor());
        existing.setNotes(updated.getNotes());
        existing.setTags(updated.getTags());

        Goal saved = goalRepository.save(existing);
        if (saved.getGoalType() == GoalType.SAVINGS_TARGET && firmId != null) {
            recalculateSavingsGoal(firmId, saved.getId());
            saved = goalRepository.findById(saved.getId()).orElse(saved);
        }
        return saved;
    }

    @Transactional
    public boolean deleteGoal(Long id) {
        Long firmId = TenantContext.getCurrentFirmId();
        if (firmId != null) {
            if (!goalRepository.existsByIdAndFirmId(id, firmId)) return false;
            // De-link savings without deleting monetary records
            savingRepository.clearGoalIdByFirmIdAndGoalId(firmId, id);
            goalRepository.deleteByIdAndFirmId(id, firmId);
            return true;
        }
        if (!goalRepository.existsById(id)) return false;
        goalRepository.deleteById(id);
        return true;
    }

    @Transactional
    public Goal checkInHabit(Long id) {
        Long firmId = TenantContext.getCurrentFirmId();
        Goal existing = (firmId != null)
                ? goalRepository.findByIdAndFirmId(id, firmId).orElseThrow(() -> new IllegalArgumentException("Goal not found"))
                : goalRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        LocalDate today = LocalDate.now();
        LocalDate lastCheckIn = existing.getLastCheckInDate();

        if (lastCheckIn != null && lastCheckIn.equals(today)) {
            // Already checked in today
            return existing;
        }

        int currentStreak = existing.getCurrentStreak() != null ? existing.getCurrentStreak() : 0;
        if (lastCheckIn != null && lastCheckIn.equals(today.minusDays(1))) {
            // Consecutive day
            currentStreak += 1;
        } else {
            // Broken streak or first check-in
            currentStreak = 1;
        }

        existing.setCurrentStreak(currentStreak);
        int longest = existing.getLongestStreak() != null ? existing.getLongestStreak() : 0;
        if (currentStreak > longest) {
            existing.setLongestStreak(currentStreak);
        }
        existing.setLastCheckInDate(today);

        // Check if milestone reached
        if (existing.getTargetValue() != null && existing.getTargetValue().compareTo(BigDecimal.ZERO) > 0) {
            if (BigDecimal.valueOf(currentStreak).compareTo(existing.getTargetValue()) >= 0) {
                existing.setStatus("ACHIEVED");
            }
        }

        return goalRepository.save(existing);
    }

    @Transactional
    public Goal addStreakDays(Long id, int days) {
        Long firmId = TenantContext.getCurrentFirmId();
        Goal existing = (firmId != null)
                ? goalRepository.findByIdAndFirmId(id, firmId).orElseThrow(() -> new IllegalArgumentException("Goal not found"))
                : goalRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        if (days <= 0) days = 1;
        int currentStreak = (existing.getCurrentStreak() != null ? existing.getCurrentStreak() : 0) + days;
        existing.setCurrentStreak(currentStreak);

        int longest = existing.getLongestStreak() != null ? existing.getLongestStreak() : 0;
        if (currentStreak > longest) {
            existing.setLongestStreak(currentStreak);
        }
        existing.setLastCheckInDate(LocalDate.now());

        if (existing.getTargetValue() != null && existing.getTargetValue().compareTo(BigDecimal.ZERO) > 0) {
            if (BigDecimal.valueOf(currentStreak).compareTo(existing.getTargetValue()) >= 0) {
                existing.setStatus("ACHIEVED");
            }
        }

        return goalRepository.save(existing);
    }

    @Transactional
    public Goal incrementProgress(Long id, BigDecimal delta) {
        Long firmId = TenantContext.getCurrentFirmId();
        Goal existing = (firmId != null)
                ? goalRepository.findByIdAndFirmId(id, firmId).orElseThrow(() -> new IllegalArgumentException("Goal not found"))
                : goalRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        if (delta == null) delta = BigDecimal.ONE;
        BigDecimal current = existing.getCurrentValue() != null ? existing.getCurrentValue() : BigDecimal.ZERO;
        BigDecimal next = current.add(delta);
        if (next.compareTo(BigDecimal.ZERO) < 0) next = BigDecimal.ZERO;
        existing.setCurrentValue(next.setScale(2, RoundingMode.HALF_UP));

        if (existing.getTargetValue() != null && existing.getTargetValue().compareTo(BigDecimal.ZERO) > 0) {
            if (next.compareTo(existing.getTargetValue()) >= 0) {
                existing.setStatus("ACHIEVED");
            }
        }

        return goalRepository.save(existing);
    }

    @Transactional
    public Goal resetQuitHabit(Long id) {
        Long firmId = TenantContext.getCurrentFirmId();
        Goal existing = (firmId != null)
                ? goalRepository.findByIdAndFirmId(id, firmId).orElseThrow(() -> new IllegalArgumentException("Goal not found"))
                : goalRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        existing.setStartDate(LocalDate.now());
        existing.setCurrentStreak(0);
        existing.setLastCheckInDate(LocalDate.now());
        existing.setStatus("ACTIVE");

        return goalRepository.save(existing);
    }

    @Transactional
    public Goal addSavingsToGoal(Long goalId, BigDecimal amount, String paymentMode, String notes) {
        Long firmId = TenantContext.getCurrentFirmId();
        Goal goal = (firmId != null)
                ? goalRepository.findByIdAndFirmId(goalId, firmId).orElseThrow(() -> new IllegalArgumentException("Goal not found"))
                : goalRepository.findById(goalId).orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid saving amount is required");
        }

        SavingRecord record = SavingRecord.builder()
                .firmId(goal.getFirmId())
                .title(goal.getTitle() + " Contribution")
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .category(goal.getTitle())
                .savingDate(LocalDate.now())
                .paymentMode(paymentMode != null && !paymentMode.isBlank() ? paymentMode : "UPI")
                .goalId(goal.getId())
                .tags(goal.getTags())
                .notes(notes != null ? notes.trim() : "Quick Goal Contribution")
                .build();

        savingRepository.save(record);
        recalculateSavingsGoal(goal.getFirmId(), goal.getId());
        return (firmId != null)
                ? goalRepository.findByIdAndFirmId(goal.getId(), firmId).orElse(goal)
                : goalRepository.findById(goal.getId()).orElse(goal);
    }

    public List<SavingRecord> getLinkedSavingsForGoal(Long goalId) {
        Long firmId = TenantContext.getCurrentFirmId();
        if (firmId == null || goalId == null) return List.of();
        return savingRepository.findByFirmIdAndGoalId(firmId, goalId);
    }

    @Transactional
    public void recalculateSavingsGoal(Long firmId, Long goalId) {
        if (goalId == null || firmId == null) return;
        Goal goal = goalRepository.findByIdAndFirmId(goalId, firmId).orElse(null);
        if (goal == null || goal.getGoalType() != GoalType.SAVINGS_TARGET) return;

        List<SavingRecord> linkedSavings = savingRepository.findByFirmIdAndGoalId(firmId, goalId);
        BigDecimal totalSaved = linkedSavings.stream()
                .map(s -> s.getAmount() != null ? s.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        goal.setCurrentValue(totalSaved);
        if (goal.getTargetValue() != null && goal.getTargetValue().compareTo(BigDecimal.ZERO) > 0) {
            if (totalSaved.compareTo(goal.getTargetValue()) >= 0) {
                goal.setStatus("ACHIEVED");
            } else if ("ACHIEVED".equalsIgnoreCase(goal.getStatus())) {
                goal.setStatus("ACTIVE");
            }
        }
        goalRepository.save(goal);
    }
}
