package com.billing.simple.billsoft.bounce.dto;

public class NewCampaignRequest {
    private Long seed;
    private String initialLevelDefinition;
    private String environment;
    private Double difficulty;

    public NewCampaignRequest() {}

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public String getInitialLevelDefinition() {
        return initialLevelDefinition;
    }

    public void setInitialLevelDefinition(String initialLevelDefinition) {
        this.initialLevelDefinition = initialLevelDefinition;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Double getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Double difficulty) {
        this.difficulty = difficulty;
    }
}
