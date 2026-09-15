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
@Table(name = "goals", indexes = {
    @Index(name = "idx_goals_firm_status", columnList = "firmId, status, id DESC")
})
public class Goal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GoalType goalType;

    @Column(precision = 15, scale = 2)
    private BigDecimal targetValue;

    @Column(precision = 15, scale = 2)
    private BigDecimal currentValue;

    @Column(length = 30)
    private String unit;

    private LocalDate startDate;
    private LocalDate targetDate;

    @Column(length = 30)
    private String status; // ACTIVE, ACHIEVED, PAUSED

    private Integer currentStreak;
    private Integer longestStreak;
    private LocalDate lastCheckInDate;

    @Column(length = 50)
    private String icon;

    @Column(length = 30)
    private String color;

    @Column(length = 1000)
    private String notes;

    @Column(length = 255)
    private String tags; // e.g. Car, Vehicle, SIP

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (startDate == null) {
            startDate = LocalDate.now();
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        if (currentValue == null) {
            currentValue = BigDecimal.ZERO;
        }
        if (targetValue == null) {
            targetValue = BigDecimal.ZERO;
        }
        if (currentStreak == null) {
            currentStreak = 0;
        }
        if (longestStreak == null) {
            longestStreak = 0;
        }
        if (goalType == null) {
            goalType = GoalType.SAVINGS_TARGET;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public GoalType getGoalType() { return goalType; }
    public void setGoalType(GoalType goalType) { this.goalType = goalType; }

    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }

    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(Integer currentStreak) { this.currentStreak = currentStreak; }

    public Integer getLongestStreak() { return longestStreak; }
    public void setLongestStreak(Integer longestStreak) { this.longestStreak = longestStreak; }

    public LocalDate getLastCheckInDate() { return lastCheckInDate; }
    public void setLastCheckInDate(LocalDate lastCheckInDate) { this.lastCheckInDate = lastCheckInDate; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static GoalBuilder builder() {
        return new GoalBuilder();
    }

    public static class GoalBuilder {
        private Long id;
        private Long firmId;
        private String title;
        private GoalType goalType;
        private BigDecimal targetValue;
        private BigDecimal currentValue;
        private String unit;
        private LocalDate startDate;
        private LocalDate targetDate;
        private String status;
        private Integer currentStreak;
        private Integer longestStreak;
        private LocalDate lastCheckInDate;
        private String icon;
        private String color;
        private String notes;
        private String tags;
        private LocalDateTime createdAt;

        public GoalBuilder id(Long id) { this.id = id; return this; }
        public GoalBuilder firmId(Long firmId) { this.firmId = firmId; return this; }
        public GoalBuilder title(String title) { this.title = title; return this; }
        public GoalBuilder goalType(GoalType goalType) { this.goalType = goalType; return this; }
        public GoalBuilder targetValue(BigDecimal targetValue) { this.targetValue = targetValue; return this; }
        public GoalBuilder currentValue(BigDecimal currentValue) { this.currentValue = currentValue; return this; }
        public GoalBuilder unit(String unit) { this.unit = unit; return this; }
        public GoalBuilder startDate(LocalDate startDate) { this.startDate = startDate; return this; }
        public GoalBuilder targetDate(LocalDate targetDate) { this.targetDate = targetDate; return this; }
        public GoalBuilder status(String status) { this.status = status; return this; }
        public GoalBuilder currentStreak(Integer currentStreak) { this.currentStreak = currentStreak; return this; }
        public GoalBuilder longestStreak(Integer longestStreak) { this.longestStreak = longestStreak; return this; }
        public GoalBuilder lastCheckInDate(LocalDate lastCheckInDate) { this.lastCheckInDate = lastCheckInDate; return this; }
        public GoalBuilder icon(String icon) { this.icon = icon; return this; }
        public GoalBuilder color(String color) { this.color = color; return this; }
        public GoalBuilder notes(String notes) { this.notes = notes; return this; }
        public GoalBuilder tags(String tags) { this.tags = tags; return this; }
        public GoalBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public Goal build() {
            Goal g = new Goal();
            g.id = this.id;
            g.firmId = this.firmId;
            g.title = this.title;
            g.goalType = this.goalType != null ? this.goalType : GoalType.SAVINGS_TARGET;
            g.targetValue = this.targetValue != null ? this.targetValue : BigDecimal.ZERO;
            g.currentValue = this.currentValue != null ? this.currentValue : BigDecimal.ZERO;
            g.unit = this.unit;
            g.startDate = this.startDate;
            g.targetDate = this.targetDate;
            g.status = this.status != null ? this.status : "ACTIVE";
            g.currentStreak = this.currentStreak != null ? this.currentStreak : 0;
            g.longestStreak = this.longestStreak != null ? this.longestStreak : 0;
            g.lastCheckInDate = this.lastCheckInDate;
            g.icon = this.icon;
            g.color = this.color;
            g.notes = this.notes;
            g.tags = this.tags;
            g.createdAt = this.createdAt;
            return g;
        }
    }
}
