package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Goal;
import com.billing.simple.billsoft.entities.GoalLog;
import com.billing.simple.billsoft.entities.GoalType;
import com.billing.simple.billsoft.entities.SavingRecord;
import com.billing.simple.billsoft.repo.GoalLogRepository;
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
import java.util.*;

@Service
public class GoalService {
    private final GoalRepository goalRepository;
    private final SavingRepository savingRepository;
    private final GoalLogRepository goalLogRepository;

    public GoalService(
            GoalRepository goalRepository,
            @Lazy SavingRepository savingRepository,
            GoalLogRepository goalLogRepository) {
        this.goalRepository = goalRepository;
        this.savingRepository = savingRepository;
        this.goalLogRepository = goalLogRepository;
    }

    private Long resolveFirmId(Long explicitFirmId) {
        Long target = explicitFirmId != null ? explicitFirmId : TenantContext.getCurrentFirmId();
        if (target == null || target <= 0) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Active firm context is required");
        }
        return target;
    }

    public List<Goal> getGoalsByFirm(Long firmId) {
        Long target = firmId != null ? firmId : TenantContext.getCurrentFirmId();
        if (target == null) return Collections.emptyList();
        List<Goal> list = goalRepository.findByFirmIdOrderByCreatedAtDesc(target);
        // Auto-update QUIT_HABIT streak days dynamically if active
        LocalDate today = LocalDate.now();
        for (Goal g : list) {
            if (g.getGoalType() == GoalType.QUIT_HABIT && "ACTIVE".equalsIgnoreCase(g.getStatus())
                    && g.getStartDate() != null) {
                long days = ChronoUnit.DAYS.between(g.getStartDate(), today);
                if (days < 0)
                    days = 0;
                g.setCurrentStreak((int) days);
                if (g.getLongestStreak() == null || days > g.getLongestStreak()) {
                    g.setLongestStreak((int) days);
                }
            }
        }
        return list;
    }

    public Goal getGoalById(Long id, Long firmId) {
        Long target = firmId != null ? firmId : TenantContext.getCurrentFirmId();
        return (target != null ? goalRepository.findByIdAndFirmId(id, target) : goalRepository.findById(id)).orElse(null);
    }

    @Transactional
    public Goal createGoal(Goal goal) {
        Long firmId = goal.getFirmId() != null ? goal.getFirmId() : TenantContext.getCurrentFirmId();
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
        Long effFirmId = saved.getFirmId() != null ? saved.getFirmId() : firmId;
        if (saved.getGoalType() == GoalType.SAVINGS_TARGET && effFirmId != null) {
            if (saved.getCurrentValue() != null && saved.getCurrentValue().compareTo(BigDecimal.ZERO) > 0) {
                List<SavingRecord> existingSavings = savingRepository.findByFirmIdAndGoalId(effFirmId, saved.getId());
                if (existingSavings.isEmpty()) {
                    SavingRecord initSaving = SavingRecord.builder()
                            .firmId(effFirmId)
                            .goalId(saved.getId())
                            .title("Initial Savings - " + saved.getTitle())
                            .amount(saved.getCurrentValue())
                            .category("General Savings")
                            .savingDate(saved.getStartDate() != null ? saved.getStartDate() : LocalDate.now())
                            .paymentMode("UPI")
                            .tags(saved.getTags())
                            .notes("Initial balance upon goal creation")
                            .build();
                    savingRepository.save(initSaving);
                }
            }
            recalculateSavingsGoal(effFirmId, saved.getId());
            saved = (effFirmId != null ? goalRepository.findByIdAndFirmId(saved.getId(), effFirmId) : goalRepository.findById(saved.getId())).orElse(saved);
        }

        // Write initial baseline log
        BigDecimal initialVal = saved.getGoalType() == GoalType.SAVINGS_TARGET
                || saved.getGoalType() == GoalType.LIFE_MILESTONE
                        ? (saved.getCurrentValue() != null ? saved.getCurrentValue() : BigDecimal.ZERO)
                        : BigDecimal.valueOf(saved.getCurrentStreak() != null ? saved.getCurrentStreak() : 0);

        GoalLog initialLog = GoalLog.builder()
                .firmId(saved.getFirmId())
                .goalId(saved.getId())
                .actionType("INITIAL")
                .deltaValue(initialVal)
                .resultingValue(initialVal)
                .logDate(saved.getStartDate() != null ? saved.getStartDate() : LocalDate.now())
                .notes("Goal created: " + saved.getTitle())
                .build();
        goalLogRepository.save(initialLog);

        return saved;
    }

    @Transactional
    public Goal updateGoal(Long id, Goal updated) {
        Long firmId = TenantContext.getCurrentFirmId();
        Goal existing = (firmId != null ? goalRepository.findByIdAndFirmId(id, firmId) : goalRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));

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
        Long effFirmId = saved.getFirmId() != null ? saved.getFirmId() : firmId;
        if (saved.getGoalType() == GoalType.SAVINGS_TARGET && effFirmId != null) {
            if (saved.getCurrentValue() != null && saved.getCurrentValue().compareTo(BigDecimal.ZERO) > 0) {
                List<SavingRecord> existingSavings = savingRepository.findByFirmIdAndGoalId(effFirmId, saved.getId());
                if (existingSavings.isEmpty()) {
                    SavingRecord initSaving = SavingRecord.builder()
                            .firmId(effFirmId)
                            .goalId(saved.getId())
                            .title("Initial Savings - " + saved.getTitle())
                            .amount(saved.getCurrentValue())
                            .category("General Savings")
                            .savingDate(saved.getStartDate() != null ? saved.getStartDate() : LocalDate.now())
                            .paymentMode("UPI")
                            .tags(saved.getTags())
                            .notes("Initial balance upon goal creation")
                            .build();
                    savingRepository.save(initSaving);
                }
            }
            recalculateSavingsGoal(effFirmId, saved.getId());
            saved = (effFirmId != null ? goalRepository.findByIdAndFirmId(saved.getId(), effFirmId) : goalRepository.findById(saved.getId())).orElse(saved);
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
            goalLogRepository.deleteByGoalIdAndFirmId(id, firmId);
            goalRepository.deleteByIdAndFirmId(id, firmId);
            return true;
        }
        if (!goalRepository.existsById(id)) return false;
        goalLogRepository.deleteByGoalId(id);
        goalRepository.deleteById(id);
        return true;
    }

    @Transactional
    public Goal checkInHabit(Long id) {
        Long firmId = TenantContext.getCurrentFirmId();
        Goal existing = (firmId != null ? goalRepository.findByIdAndFirmId(id, firmId) : goalRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));

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

        Goal saved = goalRepository.save(existing);

        GoalLog log = GoalLog.builder()
                .firmId(saved.getFirmId())
                .goalId(saved.getId())
                .actionType("CHECK_IN")
                .deltaValue(BigDecimal.ONE)
                .resultingValue(BigDecimal.valueOf(currentStreak))
                .logDate(today)
                .notes("Daily Check-In")
                .build();
        goalLogRepository.save(log);

        return saved;
    }

    @Transactional
    public Goal addStreakDays(Long id, int days) {
        Long firmId = TenantContext.getCurrentFirmId();
        Goal existing = (firmId != null ? goalRepository.findByIdAndFirmId(id, firmId) : goalRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        if (days <= 0)
            days = 1;
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

        Goal saved = goalRepository.save(existing);

        GoalLog log = GoalLog.builder()
                .firmId(saved.getFirmId())
                .goalId(saved.getId())
                .actionType("STREAK_BOOST")
                .deltaValue(BigDecimal.valueOf(days))
                .resultingValue(BigDecimal.valueOf(currentStreak))
                .logDate(LocalDate.now())
                .notes("+" + days + " Days Streak Boost")
                .build();
        goalLogRepository.save(log);

        return saved;
    }

    @Transactional
    public Goal incrementProgress(Long id, BigDecimal delta) {
        return incrementProgress(id, delta, null, null);
    }

    @Transactional
    public Goal incrementProgress(Long id, BigDecimal delta, LocalDate logDate, String notes) {
        Long firmId = TenantContext.getCurrentFirmId();
        Goal existing = (firmId != null ? goalRepository.findByIdAndFirmId(id, firmId) : goalRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        if (delta == null)
            delta = BigDecimal.ONE;
        BigDecimal current = existing.getCurrentValue() != null ? existing.getCurrentValue() : BigDecimal.ZERO;
        BigDecimal next = current.add(delta);
        if (next.compareTo(BigDecimal.ZERO) < 0)
            next = BigDecimal.ZERO;
        existing.setCurrentValue(next.setScale(2, RoundingMode.HALF_UP));

        if (existing.getTargetValue() != null && existing.getTargetValue().compareTo(BigDecimal.ZERO) > 0) {
            if (next.compareTo(existing.getTargetValue()) >= 0) {
                existing.setStatus("ACHIEVED");
            } else if ("ACHIEVED".equalsIgnoreCase(existing.getStatus())) {
                existing.setStatus("ACTIVE");
            }
        }

        Goal saved = goalRepository.save(existing);

        String defaultNote = "Milestone Step " + (delta.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + delta;
        String finalNote = (notes != null && !notes.isBlank()) ? notes.trim() : defaultNote;

        GoalLog log = GoalLog.builder()
                .firmId(saved.getFirmId())
                .goalId(saved.getId())
                .actionType(delta.compareTo(BigDecimal.ZERO) >= 0 ? "INCREMENT" : "DECREMENT")
                .deltaValue(delta)
                .resultingValue(next)
                .logDate(logDate != null ? logDate : LocalDate.now())
                .notes(finalNote)
                .build();
        goalLogRepository.save(log);

        return saved;
    }

    @Transactional
    public Goal resetQuitHabit(Long id) {
        Long firmId = TenantContext.getCurrentFirmId();
        Goal existing = (firmId != null ? goalRepository.findByIdAndFirmId(id, firmId) : goalRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        int prevStreak = existing.getCurrentStreak() != null ? existing.getCurrentStreak() : 0;
        existing.setStartDate(LocalDate.now());
        existing.setCurrentStreak(0);
        existing.setLastCheckInDate(LocalDate.now());
        existing.setStatus("ACTIVE");

        Goal saved = goalRepository.save(existing);

        GoalLog log = GoalLog.builder()
                .firmId(saved.getFirmId())
                .goalId(saved.getId())
                .actionType("RESET")
                .deltaValue(BigDecimal.valueOf(-prevStreak))
                .resultingValue(BigDecimal.ZERO)
                .logDate(LocalDate.now())
                .notes("Relapse / Counter Reset")
                .build();
        goalLogRepository.save(log);

        return saved;
    }

    @Transactional
    public Goal addSavingsToGoal(Long goalId, BigDecimal amount, String paymentMode, String notes) {
        Long firmId = TenantContext.getCurrentFirmId();
        Goal goal = (firmId != null ? goalRepository.findByIdAndFirmId(goalId, firmId) : goalRepository.findById(goalId))
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));

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
        if (goal.getFirmId() != null) {
            recalculateSavingsGoal(goal.getFirmId(), goal.getId());
        }

        Goal updatedGoal = (firmId != null ? goalRepository.findByIdAndFirmId(goal.getId(), firmId) : goalRepository.findById(goal.getId())).orElse(goal);

        GoalLog log = GoalLog.builder()
                .firmId(updatedGoal.getFirmId())
                .goalId(updatedGoal.getId())
                .actionType("DEPOSIT")
                .deltaValue(amount)
                .resultingValue(updatedGoal.getCurrentValue())
                .logDate(LocalDate.now())
                .notes(notes != null ? notes.trim() : "Goal Contribution")
                .build();
        goalLogRepository.save(log);

        return updatedGoal;
    }

    @Transactional
    public Goal deductSavingsFromGoal(Long goalId, BigDecimal amount, String paymentMode, String notes) {
        Long firmId = TenantContext.getCurrentFirmId();
        Goal goal = (firmId != null ? goalRepository.findByIdAndFirmId(goalId, firmId) : goalRepository.findById(goalId))
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid deduction amount is required");
        }

        BigDecimal negAmount = amount.abs().negate().setScale(2, RoundingMode.HALF_UP);

        SavingRecord record = SavingRecord.builder()
                .firmId(goal.getFirmId())
                .title(goal.getTitle() + " Withdrawal / Deduction")
                .amount(negAmount)
                .category(goal.getTitle())
                .savingDate(LocalDate.now())
                .paymentMode(paymentMode != null && !paymentMode.isBlank() ? paymentMode : "UPI")
                .goalId(goal.getId())
                .tags(goal.getTags())
                .notes(notes != null ? notes.trim() : "Goal Deduction")
                .build();

        savingRepository.save(record);
        if (goal.getFirmId() != null) {
            recalculateSavingsGoal(goal.getFirmId(), goal.getId());
        }

        Goal updatedGoal = (firmId != null ? goalRepository.findByIdAndFirmId(goal.getId(), firmId) : goalRepository.findById(goal.getId())).orElse(goal);

        GoalLog log = GoalLog.builder()
                .firmId(updatedGoal.getFirmId())
                .goalId(updatedGoal.getId())
                .actionType("DEDUCTION")
                .deltaValue(negAmount)
                .resultingValue(updatedGoal.getCurrentValue())
                .logDate(LocalDate.now())
                .notes(notes != null ? notes.trim() : "Goal Deduction")
                .build();
        goalLogRepository.save(log);

        return updatedGoal;
    }

    @Transactional
    public Goal reconcileGoalBalance(Long goalId, BigDecimal targetValue, String notes) {
        Long firmId = TenantContext.getCurrentFirmId();
        Goal goal = (firmId != null ? goalRepository.findByIdAndFirmId(goalId, firmId) : goalRepository.findById(goalId))
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        if (targetValue == null || targetValue.compareTo(BigDecimal.ZERO) < 0) {
            targetValue = BigDecimal.ZERO;
        }
        targetValue = targetValue.setScale(2, RoundingMode.HALF_UP);

        if (goal.getGoalType() == GoalType.SAVINGS_TARGET) {
            BigDecimal currentSaved = goal.getCurrentValue() != null ? goal.getCurrentValue() : BigDecimal.ZERO;
            BigDecimal delta = targetValue.subtract(currentSaved).setScale(2, RoundingMode.HALF_UP);

            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                SavingRecord record = SavingRecord.builder()
                        .firmId(goal.getFirmId())
                        .title(goal.getTitle() + " Balance Reconciliation")
                        .amount(delta)
                        .category(goal.getTitle())
                        .savingDate(LocalDate.now())
                        .paymentMode("Adjustment")
                        .goalId(goal.getId())
                        .tags(goal.getTags())
                        .notes(notes != null && !notes.isBlank() ? notes.trim()
                                : "Reconciled balance directly to " + targetValue)
                        .build();

                savingRepository.save(record);
                if (goal.getFirmId() != null) {
                    recalculateSavingsGoal(goal.getFirmId(), goal.getId());
                }
            } else {
                goal.setCurrentValue(targetValue);
                goalRepository.save(goal);
            }

            Goal updatedGoal = (firmId != null ? goalRepository.findByIdAndFirmId(goal.getId(), firmId) : goalRepository.findById(goal.getId())).orElse(goal);

            GoalLog log = GoalLog.builder()
                    .firmId(updatedGoal.getFirmId())
                    .goalId(updatedGoal.getId())
                    .actionType("RECONCILE")
                    .deltaValue(delta)
                    .resultingValue(targetValue)
                    .logDate(LocalDate.now())
                    .notes(notes != null && !notes.isBlank() ? notes.trim() : "Balance reconciled to " + targetValue)
                    .build();
            goalLogRepository.save(log);

            return updatedGoal;
        } else {
            // Habit or Milestone
            BigDecimal oldVal = goal.getGoalType() == GoalType.LIFE_MILESTONE
                    ? (goal.getCurrentValue() != null ? goal.getCurrentValue() : BigDecimal.ZERO)
                    : BigDecimal.valueOf(goal.getCurrentStreak() != null ? goal.getCurrentStreak() : 0);

            BigDecimal delta = targetValue.subtract(oldVal);

            if (goal.getGoalType() == GoalType.LIFE_MILESTONE) {
                goal.setCurrentValue(targetValue);
            } else {
                goal.setCurrentStreak(targetValue.intValue());
                if (goal.getLongestStreak() == null || targetValue.intValue() > goal.getLongestStreak()) {
                    goal.setLongestStreak(targetValue.intValue());
                }
            }

            if (goal.getTargetValue() != null && goal.getTargetValue().compareTo(BigDecimal.ZERO) > 0) {
                if (targetValue.compareTo(goal.getTargetValue()) >= 0) {
                    goal.setStatus("ACHIEVED");
                } else if ("ACHIEVED".equalsIgnoreCase(goal.getStatus())) {
                    goal.setStatus("ACTIVE");
                }
            }

            Goal saved = goalRepository.save(goal);

            GoalLog log = GoalLog.builder()
                    .firmId(saved.getFirmId())
                    .goalId(saved.getId())
                    .actionType("RECONCILE")
                    .deltaValue(delta)
                    .resultingValue(targetValue)
                    .logDate(LocalDate.now())
                    .notes(notes != null && !notes.isBlank() ? notes.trim()
                            : "Value updated directly to " + targetValue)
                    .build();
            goalLogRepository.save(log);

            return saved;
        }
    }

    public List<SavingRecord> getLinkedSavingsForGoal(Long goalId) {
        Long firmId = TenantContext.getCurrentFirmId();
        if (goalId == null)
            return List.of();
        return firmId != null ? savingRepository.findByFirmIdAndGoalId(firmId, goalId) : savingRepository.findByGoalId(goalId);
    }

    public Map<String, Object> getGoalTimeline(Long goalId) {
        Long firmId = TenantContext.getCurrentFirmId();
        Goal goal = (firmId != null ? goalRepository.findByIdAndFirmId(goalId, firmId) : goalRepository.findById(goalId)).orElse(null);

        if (goal == null) {
            return Map.of("points", List.of());
        }

        List<Map<String, Object>> points = new ArrayList<>();

        if (goal.getGoalType() == GoalType.SAVINGS_TARGET) {
            // Strictly financial: build chronological ledger from SavingRecord
            List<SavingRecord> records = (firmId != null ? savingRepository.findByFirmIdAndGoalId(firmId, goalId) : savingRepository.findByGoalId(goalId));

            // Sort by savingDate ASC, id ASC
            records = new ArrayList<>(records);
            records.sort(
                    Comparator.comparing(SavingRecord::getSavingDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(SavingRecord::getId, Comparator.nullsLast(Comparator.naturalOrder())));

            // Baseline origin
            LocalDate startDate = goal.getStartDate() != null ? goal.getStartDate()
                    : (records.isEmpty() ? LocalDate.now() : records.get(0).getSavingDate());
            BigDecimal runningSum = BigDecimal.ZERO;

            if (records.isEmpty() || startDate.isBefore(records.get(0).getSavingDate())) {
                points.add(Map.of(
                        "date", startDate.toString(),
                        "delta", BigDecimal.ZERO,
                        "value", BigDecimal.ZERO,
                        "type", "INITIAL",
                        "notes", "Goal Started"));
            }

            for (SavingRecord r : records) {
                BigDecimal amt = r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO;
                runningSum = runningSum.add(amt);
                if (runningSum.compareTo(BigDecimal.ZERO) < 0)
                    runningSum = BigDecimal.ZERO;

                String type = amt.compareTo(BigDecimal.ZERO) >= 0 ? "DEPOSIT" : "DEDUCTION";
                if ("Adjustment".equalsIgnoreCase(r.getPaymentMode())) {
                    type = "RECONCILE";
                }

                Map<String, Object> pt = new HashMap<>();
                pt.put("id", r.getId());
                pt.put("date", r.getSavingDate() != null ? r.getSavingDate().toString() : LocalDate.now().toString());
                pt.put("delta", amt);
                pt.put("value", runningSum);
                pt.put("type", type);
                pt.put("title", r.getTitle() != null ? r.getTitle() : "Contribution");
                pt.put("paymentMode", r.getPaymentMode() != null ? r.getPaymentMode() : "UPI");
                pt.put("notes", r.getNotes() != null ? r.getNotes() : "");
                points.add(pt);
            }
        } else {
            // Habit or Milestone: build chronological ledger from GoalLog
            List<GoalLog> logs = (firmId != null)
                    ? goalLogRepository.findByGoalIdAndFirmIdOrderByLogDateAscCreatedAtAsc(goalId, firmId)
                    : goalLogRepository.findByGoalIdOrderByLogDateAscCreatedAtAsc(goalId);

            if (logs.isEmpty()) {
                BigDecimal currentVal = goal.getGoalType() == GoalType.LIFE_MILESTONE
                        ? (goal.getCurrentValue() != null ? goal.getCurrentValue() : BigDecimal.ZERO)
                        : BigDecimal.valueOf(goal.getCurrentStreak() != null ? goal.getCurrentStreak() : 0);

                LocalDate startDate = goal.getStartDate() != null ? goal.getStartDate() : LocalDate.now();
                points.add(Map.of(
                        "date", startDate.toString(),
                        "delta", currentVal,
                        "value", currentVal,
                        "type", "INITIAL",
                        "notes", "Goal Created"));
            } else {
                for (GoalLog log : logs) {
                    Map<String, Object> pt = new HashMap<>();
                    pt.put("id", log.getId());
                    pt.put("date", log.getLogDate() != null ? log.getLogDate().toString() : LocalDate.now().toString());
                    pt.put("delta", log.getDeltaValue() != null ? log.getDeltaValue() : BigDecimal.ZERO);
                    pt.put("value", log.getResultingValue() != null ? log.getResultingValue() : BigDecimal.ZERO);
                    pt.put("type", log.getActionType() != null ? log.getActionType() : "LOG");
                    pt.put("notes", log.getNotes() != null ? log.getNotes() : "");
                    points.add(pt);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("goalId", goal.getId());
        result.put("title", goal.getTitle());
        result.put("goalType", goal.getGoalType() != null ? goal.getGoalType().name() : "SAVINGS_TARGET");
        result.put("targetValue", goal.getTargetValue() != null ? goal.getTargetValue() : BigDecimal.ZERO);
        result.put("currentValue", goal.getCurrentValue() != null ? goal.getCurrentValue() : BigDecimal.ZERO);
        result.put("currentStreak", goal.getCurrentStreak() != null ? goal.getCurrentStreak() : 0);
        result.put("longestStreak", goal.getLongestStreak() != null ? goal.getLongestStreak() : 0);
        result.put("unit", goal.getUnit() != null ? goal.getUnit() : "");
        result.put("color", goal.getColor() != null ? goal.getColor() : "#10b981");
        result.put("points", points);

        return result;
    }

    @Transactional
    public void recalculateSavingsGoal(Long firmId, Long goalId) {
        if (goalId == null || firmId == null)
            return;
        Goal goal = goalRepository.findByIdAndFirmId(goalId, firmId).orElse(null);
        if (goal == null || goal.getGoalType() != GoalType.SAVINGS_TARGET)
            return;

        List<SavingRecord> linkedSavings = savingRepository.findByFirmIdAndGoalId(firmId, goalId);
        BigDecimal totalSaved = linkedSavings.stream()
                .map(s -> s.getAmount() != null ? s.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (totalSaved.compareTo(BigDecimal.ZERO) < 0)
            totalSaved = BigDecimal.ZERO;

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
