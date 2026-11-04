package com.billing.simple.billsoft.bounce.dto;

public class SaveProgressRequest {
    private Long campaignId;
    private int levelNumber;
    private double playerX;
    private double playerY;
    private double playerVx;
    private double playerVy;
    private String ballState;
    private int remainingLives;
    private String collectedHoopsJson;
    private String objectStatesJson;
    private String checkpointId;
    private long currentScore;
    private double gameplayTimeSeconds;
    private int snapshotVersion;

    public SaveProgressRequest() {}

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
}
