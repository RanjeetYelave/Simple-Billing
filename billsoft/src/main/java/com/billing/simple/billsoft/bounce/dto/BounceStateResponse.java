package com.billing.simple.billsoft.bounce.dto;

import com.billing.simple.billsoft.bounce.entities.BounceCampaign;
import com.billing.simple.billsoft.bounce.entities.BounceLevel;
import com.billing.simple.billsoft.bounce.entities.BounceLevelRecord;
import com.billing.simple.billsoft.bounce.entities.BounceProgress;
import com.billing.simple.billsoft.bounce.entities.BounceSettings;

import java.util.List;

public class BounceStateResponse {
    private BounceCampaign campaign;
    private BounceLevel currentLevel;
    private BounceProgress progress;
    private BounceSettings settings;
    private List<BounceLevelRecord> records;

    public BounceStateResponse() {}

    public BounceStateResponse(BounceCampaign campaign, BounceLevel currentLevel, BounceProgress progress, BounceSettings settings, List<BounceLevelRecord> records) {
        this.campaign = campaign;
        this.currentLevel = currentLevel;
        this.progress = progress;
        this.settings = settings;
        this.records = records;
    }

    public BounceCampaign getCampaign() {
        return campaign;
    }

    public void setCampaign(BounceCampaign campaign) {
        this.campaign = campaign;
    }

    public BounceLevel getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(BounceLevel currentLevel) {
        this.currentLevel = currentLevel;
    }

    public BounceProgress getProgress() {
        return progress;
    }

    public void setProgress(BounceProgress progress) {
        this.progress = progress;
    }

    public BounceSettings getSettings() {
        return settings;
    }

    public void setSettings(BounceSettings settings) {
        this.settings = settings;
    }

    public List<BounceLevelRecord> getRecords() {
        return records;
    }

    public void setRecords(List<BounceLevelRecord> records) {
        this.records = records;
    }
}
