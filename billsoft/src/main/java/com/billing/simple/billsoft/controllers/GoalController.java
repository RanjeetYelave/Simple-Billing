package com.billing.simple.billsoft.controllers;

import com.billing.simple.billsoft.entities.Goal;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.GoalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

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
            @RequestBody java.util.Map<String, Object> payload) {
        BigDecimal amount = BigDecimal.valueOf(Double.parseDouble(payload.getOrDefault("amount", "1000").toString()));
        String paymentMode = payload.getOrDefault("paymentMode", "UPI").toString();
        String notes = payload.getOrDefault("notes", "Quick Goal Contribution").toString();
        return service.addSavingsToGoal(id, amount, paymentMode, notes);
    }

    @GetMapping("/{id}/savings")
    public List<com.billing.simple.billsoft.entities.SavingRecord> getLinkedSavings(@PathVariable Long id) {
        return service.getLinkedSavingsForGoal(id);
    }

    @PostMapping("/{id}/increment")
    public Goal increment(@PathVariable Long id, @RequestParam(required = false) BigDecimal delta) {
        return service.incrementProgress(id, delta);
    }

    @PostMapping("/{id}/reset")
    public Goal reset(@PathVariable Long id) {
        return service.resetQuitHabit(id);
    }
}
