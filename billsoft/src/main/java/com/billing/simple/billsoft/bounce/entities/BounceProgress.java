package com.billing.simple.billsoft.bounce.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bounce_progress", indexes = {
    @Index(name = "idx_bounce_prog_camp", columnList = "campaign_id", unique = true)
})
public class BounceProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false, unique = true)
    private Long campaignId;

    @Column(name = "level_number", nullable = false)
    private int levelNumber = 1;

    @Column(name = "player_x", nullable = false)
    private double playerX;

    @Column(name = "player_y", nullable = false)
    private double playerY;

    @Column(name = "player_vx", nullable = false)
    private double playerVx;

    @Column(name = "player_vy", nullable = false)
    private double playerVy;

    @Column(name = "ball_state", nullable = false, length = 32)
    private String ballState = "NORMAL";

    @Column(name = "remaining_lives", nullable = false)
    private int remainingLives = 3;

    @Lob
    @Column(name = "collected_hoops_json", columnDefinition = "TEXT")
    private String collectedHoopsJson = "[]";

    @Lob
    @Column(name = "object_states_json", columnDefinition = "TEXT")
    private String objectStatesJson = "{}";

    @Column(name = "checkpoint_id", length = 64)
    private String checkpointId;

    @Column(name = "current_score", nullable = false)
    private long currentScore = 0;

    @Column(name = "gameplay_time_seconds", nullable = false)
    private double gameplayTimeSeconds = 0.0;

    @Column(name = "snapshot_version", nullable = false)
    private int snapshotVersion = 1;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public BounceProgress() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public int getLevelNumber() {
        return levelNumber;
    }

    public void setLevelNumber(int levelNumber) {
        this.levelNumber = levelNumber;
    }

    public double getPlayerX() {
        return playerX;
    }

    public void setPlayerX(double playerX) {
        this.playerX = playerX;
    }

    public double getPlayerY() {
        return playerY;
    }

    public void setPlayerY(double playerY) {
        this.playerY = playerY;
    }

    public double getPlayerVx() {
        return playerVx;
    }

    public void setPlayerVx(double playerVx) {
        this.playerVx = playerVx;
    }

    public double getPlayerVy() {
        return playerVy;
    }

    public void setPlayerVy(double playerVy) {
        this.playerVy = playerVy;
    }

    public String getBallState() {
        return ballState;
    }

    public void setBallState(String ballState) {
        this.ballState = ballState;
    }

    public int getRemainingLives() {
        return remainingLives;
    }

    public void setRemainingLives(int remainingLives) {
        this.remainingLives = remainingLives;
    }

    public String getCollectedHoopsJson() {
        return collectedHoopsJson;
    }

    public void setCollectedHoopsJson(String collectedHoopsJson) {
        this.collectedHoopsJson = collectedHoopsJson;
    }

    public String getObjectStatesJson() {
        return objectStatesJson;
    }

    public void setObjectStatesJson(String objectStatesJson) {
        this.objectStatesJson = objectStatesJson;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public void setCheckpointId(String checkpointId) {
        this.checkpointId = checkpointId;
    }

    public long getCurrentScore() {
        return currentScore;
    }

    public void setCurrentScore(long currentScore) {
        this.currentScore = currentScore;
    }

    public double getGameplayTimeSeconds() {
        return gameplayTimeSeconds;
    }

    public void setGameplayTimeSeconds(double gameplayTimeSeconds) {
        this.gameplayTimeSeconds = gameplayTimeSeconds;
    }

    public int getSnapshotVersion() {
        return snapshotVersion;
    }

    public void setSnapshotVersion(int snapshotVersion) {
        this.snapshotVersion = snapshotVersion;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
