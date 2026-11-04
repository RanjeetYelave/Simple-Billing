package com.billing.simple.billsoft.bounce.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bounce_level_record", indexes = {
    @Index(name = "idx_bounce_record_lvl", columnList = "level_number", unique = true)
})
public class BounceLevelRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "level_number", nullable = false, unique = true)
    private int levelNumber;

    @Column(name = "best_time_seconds", nullable = false)
    private double bestTimeSeconds = 0.0;

    @Column(name = "best_score", nullable = false)
    private long bestScore = 0;

    @Column(name = "best_hoops_collected", nullable = false)
    private int bestHoopsCollected = 0;

    @Column(name = "total_hoops", nullable = false)
    private int totalHoops = 0;

    @Column(name = "completion_status", nullable = false, length = 32)
    private String completionStatus = "COMPLETED";

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 1;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt = LocalDateTime.now();

    public BounceLevelRecord() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getLevelNumber() {
        return levelNumber;
    }

    public void setLevelNumber(int levelNumber) {
        this.levelNumber = levelNumber;
    }

    public double getBestTimeSeconds() {
        return bestTimeSeconds;
    }

    public void setBestTimeSeconds(double bestTimeSeconds) {
        this.bestTimeSeconds = bestTimeSeconds;
    }

    public long getBestScore() {
        return bestScore;
    }

    public void setBestScore(long bestScore) {
        this.bestScore = bestScore;
    }

    public int getBestHoopsCollected() {
        return bestHoopsCollected;
    }

    public void setBestHoopsCollected(int bestHoopsCollected) {
        this.bestHoopsCollected = bestHoopsCollected;
    }

    public int getTotalHoops() {
        return totalHoops;
    }

    public void setTotalHoops(int totalHoops) {
        this.totalHoops = totalHoops;
    }

    public String getCompletionStatus() {
        return completionStatus;
    }

    public void setCompletionStatus(String completionStatus) {
        this.completionStatus = completionStatus;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
