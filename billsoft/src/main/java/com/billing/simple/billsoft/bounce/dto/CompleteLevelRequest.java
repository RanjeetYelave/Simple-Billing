package com.billing.simple.billsoft.bounce.dto;

public class CompleteLevelRequest {
    private Long campaignId;
    private int levelNumber;
    private double completionTimeSeconds;
    private long score;
    private int hoopsCollected;
    private int totalHoops;
    private String nextLevelDefinition;
    private Long nextLevelSeed;
    private String nextEnvironment;
    private Double nextDifficulty;

    public CompleteLevelRequest() {}

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

    public double getCompletionTimeSeconds() {
        return completionTimeSeconds;
    }

    public void setCompletionTimeSeconds(double completionTimeSeconds) {
        this.completionTimeSeconds = completionTimeSeconds;
    }

    public long getScore() {
        return score;
    }

    public void setScore(long score) {
        this.score = score;
    }

    public int getHoopsCollected() {
        return hoopsCollected;
    }

    public void setHoopsCollected(int hoopsCollected) {
        this.hoopsCollected = hoopsCollected;
    }

    public int getTotalHoops() {
        return totalHoops;
    }

    public void setTotalHoops(int totalHoops) {
        this.totalHoops = totalHoops;
    }

    public String getNextLevelDefinition() {
        return nextLevelDefinition;
    }

    public void setNextLevelDefinition(String nextLevelDefinition) {
        this.nextLevelDefinition = nextLevelDefinition;
    }

    public Long getNextLevelSeed() {
        return nextLevelSeed;
    }

    public void setNextLevelSeed(Long nextLevelSeed) {
        this.nextLevelSeed = nextLevelSeed;
    }

    public String getNextEnvironment() {
        return nextEnvironment;
    }

    public void setNextEnvironment(String nextEnvironment) {
        this.nextEnvironment = nextEnvironment;
    }

    public Double getNextDifficulty() {
        return nextDifficulty;
    }

    public void setNextDifficulty(Double nextDifficulty) {
        this.nextDifficulty = nextDifficulty;
    }
}
