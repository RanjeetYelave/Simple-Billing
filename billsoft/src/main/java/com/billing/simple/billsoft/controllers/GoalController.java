package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.Goal;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.GoalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/goals")
public class GoalController {
    private final GoalService service;

    public GoalController(GoalService service) {
        this.service = service;
    }

    @GetMapping
    public List<Goal> listByFirm(@RequestParam(required = false) Long firmId) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId == null) authoritativeFirmId = firmId;
        return service.getGoalsByFirm(authoritativeFirmId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Goal> getById(@PathVariable Long id) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        Goal g = service.getGoalById(id, authoritativeFirmId);
        return g != null ? ResponseEntity.ok(g) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public Goal create(@RequestBody Goal goal) {
        return service.createGoal(goal);
    }

    @PutMapping("/{id}")
    public Goal update(@PathVariable Long id, @RequestBody Goal goal) {
        return service.updateGoal(id, goal);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        boolean deleted = service.deleteGoal(id);
        return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/check-in")
    public Goal checkIn(@PathVariable Long id) {
        return service.checkInHabit(id);
    }

    @PostMapping("/{id}/increment-streak")
    public Goal incrementStreak(@PathVariable Long id, @RequestParam(defaultValue = "1") int days) {
        return service.addStreakDays(id, days);
    }

    @PostMapping("/{id}/quick-saving")
    public Goal quickSaving(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        BigDecimal amount = BigDecimal.valueOf(Double.parseDouble(payload.getOrDefault("amount", "1000").toString()));
        String paymentMode = payload.getOrDefault("paymentMode", "UPI").toString();
        String notes = payload.getOrDefault("notes", "Quick Goal Contribution").toString();
        return service.addSavingsToGoal(id, amount, paymentMode, notes);
    }

    @PostMapping("/{id}/deduct")
    public Goal deductSaving(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        BigDecimal amount = BigDecimal.valueOf(Double.parseDouble(payload.getOrDefault("amount", "1000").toString()));
        String paymentMode = payload.getOrDefault("paymentMode", "UPI").toString();
        String notes = payload.getOrDefault("notes", "Goal Deduction / Withdrawal").toString();
        return service.deductSavingsFromGoal(id, amount, paymentMode, notes);
    }

    @PostMapping("/{id}/reconcile")
    public Goal reconcile(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        BigDecimal targetValue = BigDecimal.valueOf(Double.parseDouble(payload.getOrDefault("targetValue", "0").toString()));
        String notes = payload.getOrDefault("notes", "").toString();
        return service.reconcileGoalBalance(id, targetValue, notes);
    }

    @GetMapping("/{id}/savings")
    public List<com.billing.simple.billsoft.entities.SavingRecord> getLinkedSavings(@PathVariable Long id) {
        return service.getLinkedSavingsForGoal(id);
    }

    @GetMapping("/{id}/timeline")
    public Map<String, Object> getTimeline(@PathVariable Long id) {
        return service.getGoalTimeline(id);
    }

    @PostMapping("/{id}/increment")
    public Goal increment(
            @PathVariable Long id,
            @RequestParam(required = false) BigDecimal delta,
            @RequestBody(required = false) Map<String, Object> payload) {
        BigDecimal effectiveDelta = delta;
        LocalDate logDate = null;
        String notes = null;
        if (payload != null) {
            if (payload.containsKey("delta") && payload.get("delta") != null) {
                effectiveDelta = BigDecimal.valueOf(Double.parseDouble(payload.get("delta").toString()));
            }
            if (payload.containsKey("logDate") && payload.get("logDate") != null && !payload.get("logDate").toString().isBlank()) {
                try {
                    logDate = LocalDate.parse(payload.get("logDate").toString());
                } catch (Exception ignored) {}
            }
            if (payload.containsKey("notes") && payload.get("notes") != null) {
                notes = payload.get("notes").toString();
            }
        }
        return service.incrementProgress(id, effectiveDelta, logDate, notes);
    }

    @PostMapping("/{id}/reset")
    public Goal reset(@PathVariable Long id) {
        return service.resetQuitHabit(id);
    }
}
