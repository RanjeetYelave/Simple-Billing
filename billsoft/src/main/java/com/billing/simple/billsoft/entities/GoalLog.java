package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "goal_logs", indexes = {
    @Index(name = "idx_goal_logs_firm_goal", columnList = "firmId, goalId, logDate ASC, createdAt ASC")
})
public class GoalLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private Long goalId;

    @Column(nullable = false, length = 50)
    private String actionType; // INITIAL, CHECK_IN, STREAK_BOOST, RESET, INCREMENT, DECREMENT, RECONCILE, DEPOSIT, DEDUCTION

    @Column(precision = 15, scale = 2)
    private BigDecimal deltaValue;

    @Column(precision = 15, scale = 2)
    private BigDecimal resultingValue;

    private LocalDate logDate;

    @Column(length = 500)
    private String notes;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (logDate == null) {
            logDate = LocalDate.now();
        }
        if (deltaValue == null) {
            deltaValue = BigDecimal.ZERO;
        }
        if (resultingValue == null) {
            resultingValue = BigDecimal.ZERO;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public Long getGoalId() { return goalId; }
    public void setGoalId(Long goalId) { this.goalId = goalId; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public BigDecimal getDeltaValue() { return deltaValue; }
    public void setDeltaValue(BigDecimal deltaValue) { this.deltaValue = deltaValue; }

    public BigDecimal getResultingValue() { return resultingValue; }
    public void setResultingValue(BigDecimal resultingValue) { this.resultingValue = resultingValue; }

    public LocalDate getLogDate() { return logDate; }
    public void setLogDate(LocalDate logDate) { this.logDate = logDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
