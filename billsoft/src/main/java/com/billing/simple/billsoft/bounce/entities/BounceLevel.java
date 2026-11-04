package com.billing.simple.billsoft.bounce.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bounce_level", indexes = {
    @Index(name = "idx_bounce_level_camp_num", columnList = "campaign_id, level_number")
})
public class BounceLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "level_number", nullable = false)
    private int levelNumber;

    @Column(name = "seed", nullable = false)
    private long seed;

    @Column(name = "generator_version", nullable = false)
    private int generatorVersion = 1;

    @Column(name = "physics_version", nullable = false)
    private int physicsVersion = 1;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    @Lob
    @Column(name = "level_definition", nullable = false, columnDefinition = "TEXT")
    private String levelDefinition;

    @Column(name = "difficulty", nullable = false)
    private double difficulty = 1.0;

    @Column(name = "environment", nullable = false, length = 64)
    private String environment = "classic_brick";

    @Column(name = "generation_status", nullable = false, length = 32)
    private String generationStatus = "VALIDATED";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public BounceLevel() {}

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

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public int getGeneratorVersion() {
        return generatorVersion;
    }

    public void setGeneratorVersion(int generatorVersion) {
        this.generatorVersion = generatorVersion;
    }

    public int getPhysicsVersion() {
        return physicsVersion;
    }

    public void setPhysicsVersion(int physicsVersion) {
        this.physicsVersion = physicsVersion;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getLevelDefinition() {
        return levelDefinition;
    }

    public void setLevelDefinition(String levelDefinition) {
        this.levelDefinition = levelDefinition;
    }

    public double getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(double difficulty) {
        this.difficulty = difficulty;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getGenerationStatus() {
        return generationStatus;
    }

    public void setGenerationStatus(String generationStatus) {
        this.generationStatus = generationStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
