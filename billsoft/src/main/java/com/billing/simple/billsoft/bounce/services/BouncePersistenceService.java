package com.billing.simple.billsoft.bounce.services;

import com.billing.simple.billsoft.bounce.dto.*;
import com.billing.simple.billsoft.bounce.entities.*;
import com.billing.simple.billsoft.bounce.repositories.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@Transactional
public class BouncePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(BouncePersistenceService.class);

    private final BounceCampaignRepository campaignRepo;
    private final BounceLevelRepository levelRepo;
    private final BounceProgressRepository progressRepo;
    private final BounceLevelRecordRepository recordRepo;
    private final BounceSettingsRepository settingsRepo;

    public BouncePersistenceService(
            BounceCampaignRepository campaignRepo,
            BounceLevelRepository levelRepo,
            BounceProgressRepository progressRepo,
            BounceLevelRecordRepository recordRepo,
            BounceSettingsRepository settingsRepo
    ) {
        this.campaignRepo = campaignRepo;
        this.levelRepo = levelRepo;
        this.progressRepo = progressRepo;
        this.recordRepo = recordRepo;
        this.settingsRepo = settingsRepo;
    }

    public BounceStateResponse loadGameState() {
        BounceSettings settings = getOrCreateSettings();
        BounceCampaign campaign = campaignRepo.findFirstByCampaignStatusOrderByUpdatedAtDesc("ACTIVE")
                .orElseGet(this::createInitialCampaign);

        BounceLevel currentLevel = levelRepo.findByCampaignIdAndLevelNumber(campaign.getId(), campaign.getCurrentLevelNumber())
                .orElseGet(() -> createDefaultLevelForCampaign(campaign, campaign.getCurrentLevelNumber()));

        BounceProgress progress = progressRepo.findByCampaignId(campaign.getId())
                .orElseGet(() -> createInitialProgress(campaign.getId(), campaign.getCurrentLevelNumber()));

        List<BounceLevelRecord> records = recordRepo.findAllByOrderByLevelNumberAsc();

        return new BounceStateResponse(campaign, currentLevel, progress, settings, records);
    }

    public BounceStateResponse startNewCampaign(NewCampaignRequest req) {
        // Archive existing active campaign
        campaignRepo.findFirstByCampaignStatusOrderByUpdatedAtDesc("ACTIVE").ifPresent(c -> {
            c.setCampaignStatus("ARCHIVED");
            c.setUpdatedAt(LocalDateTime.now());
            campaignRepo.save(c);
        });

        long seed = (req != null && req.getSeed() != null) ? req.getSeed() : (100000L + new Random().nextInt(900000));
        BounceCampaign campaign = new BounceCampaign();
        campaign.setCampaignStatus("ACTIVE");
        campaign.setCurrentLevelNumber(1);
        campaign.setSeed(seed);
        campaign.setConfigVersion(1);
        campaign.setCreatedAt(LocalDateTime.now());
        campaign.setUpdatedAt(LocalDateTime.now());
        campaign = campaignRepo.save(campaign);

        String def = (req != null && req.getInitialLevelDefinition() != null) ? req.getInitialLevelDefinition() : createCanonicalLevel1Json();
        String env = (req != null && req.getEnvironment() != null) ? req.getEnvironment() : "classic_brick";
        double diff = (req != null && req.getDifficulty() != null) ? req.getDifficulty() : 1.0;

        BounceLevel level = new BounceLevel();
        level.setCampaignId(campaign.getId());
        level.setLevelNumber(1);
        level.setSeed(seed);
        level.setGeneratorVersion(1);
        level.setPhysicsVersion(1);
        level.setSchemaVersion(1);
        level.setLevelDefinition(def);
        level.setDifficulty(diff);
        level.setEnvironment(env);
        level.setGenerationStatus("VALIDATED");
        level.setCreatedAt(LocalDateTime.now());
        level = levelRepo.save(level);

        campaign.setCurrentLevelId(level.getId());
        campaign = campaignRepo.save(campaign);

        BounceProgress progress = createInitialProgress(campaign.getId(), 1);
        BounceSettings settings = getOrCreateSettings();
        List<BounceLevelRecord> records = recordRepo.findAllByOrderByLevelNumberAsc();

        return new BounceStateResponse(campaign, level, progress, settings, records);
    }

    public BounceProgress saveProgress(SaveProgressRequest req) {
        if (req == null || req.getCampaignId() == null) {
            throw new IllegalArgumentException("Campaign ID is required for saving progress");
        }

        BounceProgress progress = progressRepo.findByCampaignId(req.getCampaignId())
                .orElseGet(() -> {
                    BounceProgress p = new BounceProgress();
                    p.setCampaignId(req.getCampaignId());
                    return p;
                });

        progress.setLevelNumber(req.getLevelNumber() > 0 ? req.getLevelNumber() : 1);
        progress.setPlayerX(req.getPlayerX());
        progress.setPlayerY(req.getPlayerY());
        progress.setPlayerVx(req.getPlayerVx());
        progress.setPlayerVy(req.getPlayerVy());
        progress.setBallState(req.getBallState() != null ? req.getBallState() : "NORMAL");
        progress.setRemainingLives(req.getRemainingLives() >= 0 ? req.getRemainingLives() : 3);
        progress.setCollectedHoopsJson(req.getCollectedHoopsJson() != null ? req.getCollectedHoopsJson() : "[]");
        progress.setObjectStatesJson(req.getObjectStatesJson() != null ? req.getObjectStatesJson() : "{}");
        progress.setCheckpointId(req.getCheckpointId());
        progress.setCurrentScore(Math.max(0, req.getCurrentScore()));
        progress.setGameplayTimeSeconds(Math.max(0.0, req.getGameplayTimeSeconds()));
        progress.setSnapshotVersion(req.getSnapshotVersion() > 0 ? req.getSnapshotVersion() : progress.getSnapshotVersion() + 1);
        progress.setUpdatedAt(LocalDateTime.now());

        return progressRepo.save(progress);
    }

    public BounceStateResponse completeLevel(CompleteLevelRequest req) {
        if (req == null || req.getCampaignId() == null) {
            throw new IllegalArgumentException("Campaign ID is required for level completion");
        }

        BounceCampaign campaign = campaignRepo.findById(req.getCampaignId())
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + req.getCampaignId()));

        int completedLvl = req.getLevelNumber();

        // 1. Update or create level record
        BounceLevelRecord record = recordRepo.findByLevelNumber(completedLvl).orElseGet(() -> {
            BounceLevelRecord r = new BounceLevelRecord();
            r.setLevelNumber(completedLvl);
            r.setAttemptCount(0);
            return r;
        });

        record.setAttemptCount(record.getAttemptCount() + 1);
        if (record.getBestScore() == 0 || req.getScore() > record.getBestScore()) {
            record.setBestScore(req.getScore());
        }
        if (record.getBestTimeSeconds() <= 0.001 || req.getCompletionTimeSeconds() < record.getBestTimeSeconds()) {
            record.setBestTimeSeconds(req.getCompletionTimeSeconds());
        }
        record.setBestHoopsCollected(Math.max(record.getBestHoopsCollected(), req.getHoopsCollected()));
        record.setTotalHoops(Math.max(record.getTotalHoops(), req.getTotalHoops()));
        record.setCompletionStatus("COMPLETED");
        record.setCompletedAt(LocalDateTime.now());
        recordRepo.save(record);

        // 2. Advance Campaign level
        int nextLvlNum = completedLvl + 1;
        campaign.setCurrentLevelNumber(nextLvlNum);
        campaign.setUpdatedAt(LocalDateTime.now());

        // 3. Save Next Level Definition if provided
        BounceLevel nextLevel;
        Optional<BounceLevel> existingNext = levelRepo.findByCampaignIdAndLevelNumber(campaign.getId(), nextLvlNum);
        if (existingNext.isPresent()) {
            nextLevel = existingNext.get();
        } else {
            nextLevel = new BounceLevel();
            nextLevel.setCampaignId(campaign.getId());
            nextLevel.setLevelNumber(nextLvlNum);
            nextLevel.setSeed(req.getNextLevelSeed() != null ? req.getNextLevelSeed() : (campaign.getSeed() + nextLvlNum * 31L));
            nextLevel.setGeneratorVersion(1);
            nextLevel.setPhysicsVersion(1);
            nextLevel.setSchemaVersion(1);
            nextLevel.setLevelDefinition(req.getNextLevelDefinition() != null ? req.getNextLevelDefinition() : createCanonicalLevelJson(nextLvlNum, nextLevel.getSeed()));
            nextLevel.setDifficulty(req.getNextDifficulty() != null ? req.getNextDifficulty() : (1.0 + (nextLvlNum * 0.1)));
            nextLevel.setEnvironment(req.getNextEnvironment() != null ? req.getNextEnvironment() : selectEnvironmentForLevel(nextLvlNum));
            nextLevel.setGenerationStatus("VALIDATED");
            nextLevel.setCreatedAt(LocalDateTime.now());
            nextLevel = levelRepo.save(nextLevel);
        }

        campaign.setCurrentLevelId(nextLevel.getId());
        campaign = campaignRepo.save(campaign);

        // 4. Reset progress snapshot for the fresh next level
        BounceProgress progress = progressRepo.findByCampaignId(campaign.getId())
                .orElseGet(() -> new BounceProgress());
        progress.setCampaignId(campaign.getId());
        progress.setLevelNumber(nextLvlNum);
        progress.setPlayerX(64.0);
        progress.setPlayerY(200.0);
        progress.setPlayerVx(0.0);
        progress.setPlayerVy(0.0);
        progress.setBallState("NORMAL");
        progress.setRemainingLives(3);
        progress.setCollectedHoopsJson("[]");
        progress.setObjectStatesJson("{}");
        progress.setCheckpointId(null);
        progress.setCurrentScore(req.getScore());
        progress.setGameplayTimeSeconds(0.0);
        progress.setSnapshotVersion(1);
        progress.setUpdatedAt(LocalDateTime.now());
        progress = progressRepo.save(progress);

        BounceSettings settings = getOrCreateSettings();
        List<BounceLevelRecord> records = recordRepo.findAllByOrderByLevelNumberAsc();

        return new BounceStateResponse(campaign, nextLevel, progress, settings, records);
    }

    public BounceStateResponse resetCampaign() {
        campaignRepo.findFirstByCampaignStatusOrderByUpdatedAtDesc("ACTIVE").ifPresent(c -> {
            c.setCampaignStatus("RESET");
            c.setUpdatedAt(LocalDateTime.now());
            campaignRepo.save(c);
            progressRepo.deleteByCampaignId(c.getId());
        });

        return startNewCampaign(null);
    }

    public Optional<BounceLevel> getStoredLevel(int levelNumber) {
        return levelRepo.findFirstByLevelNumberOrderByCreatedAtDesc(levelNumber);
    }

    public List<BounceLevelRecord> getRecords() {
        return recordRepo.findAllByOrderByLevelNumberAsc();
    }

    public BounceSettings updateSettings(UpdateSettingsRequest req) {
        BounceSettings settings = getOrCreateSettings();
        if (req != null) {
            settings.setMusicVolume(Math.max(0.0, Math.min(1.0, req.getMusicVolume())));
            settings.setSoundVolume(Math.max(0.0, Math.min(1.0, req.getSoundVolume())));
            settings.setMuted(req.isMuted());
            settings.setUpdatedAt(LocalDateTime.now());
            settings = settingsRepo.save(settings);
        }
        return settings;
    }

    public BounceSettings getOrCreateSettings() {
        return settingsRepo.findById(1L).orElseGet(() -> {
            BounceSettings s = new BounceSettings();
            s.setId(1L);
            s.setMusicVolume(0.7);
            s.setSoundVolume(0.8);
            s.setMuted(false);
            s.setUpdatedAt(LocalDateTime.now());
            return settingsRepo.save(s);
        });
    }

    private BounceCampaign createInitialCampaign() {
        BounceCampaign c = new BounceCampaign();
        c.setCampaignStatus("ACTIVE");
        c.setCurrentLevelNumber(1);
        c.setSeed(427210L);
        c.setConfigVersion(1);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        c = campaignRepo.save(c);

        BounceLevel lvl = createDefaultLevelForCampaign(c, 1);
        c.setCurrentLevelId(lvl.getId());
        return campaignRepo.save(c);
    }

    private BounceLevel createDefaultLevelForCampaign(BounceCampaign c, int levelNumber) {
        BounceLevel lvl = new BounceLevel();
        lvl.setCampaignId(c.getId());
        lvl.setLevelNumber(levelNumber);
        lvl.setSeed(c.getSeed() + levelNumber * 100L);
        lvl.setGeneratorVersion(1);
        lvl.setPhysicsVersion(1);
        lvl.setSchemaVersion(1);
        lvl.setLevelDefinition(createCanonicalLevelJson(levelNumber, lvl.getSeed()));
        lvl.setDifficulty(1.0 + (levelNumber - 1) * 0.1);
        lvl.setEnvironment(selectEnvironmentForLevel(levelNumber));
        lvl.setGenerationStatus("VALIDATED");
        lvl.setCreatedAt(LocalDateTime.now());
        return levelRepo.save(lvl);
    }

    private BounceProgress createInitialProgress(Long campaignId, int levelNumber) {
        BounceProgress p = new BounceProgress();
        p.setCampaignId(campaignId);
        p.setLevelNumber(levelNumber);
        p.setPlayerX(64.0);
        p.setPlayerY(200.0);
        p.setPlayerVx(0.0);
        p.setPlayerVy(0.0);
        p.setBallState("NORMAL");
        p.setRemainingLives(3);
        p.setCollectedHoopsJson("[]");
        p.setObjectStatesJson("{}");
        p.setCheckpointId(null);
        p.setCurrentScore(0);
        p.setGameplayTimeSeconds(0.0);
        p.setSnapshotVersion(1);
        p.setUpdatedAt(LocalDateTime.now());
        return progressRepo.save(p);
    }

    private String selectEnvironmentForLevel(int levelNumber) {
        int mod = (levelNumber - 1) % 4;
        switch (mod) {
            case 0: return "classic_brick";
            case 1: return "cavern_basalt";
            case 2: return "industrial_foundry";
            case 3: return "mystic_ruins";
            default: return "classic_brick";
        }
    }

    private String createCanonicalLevel1Json() {
        return createCanonicalLevelJson(1, 427210L);
    }

    private String createCanonicalLevelJson(int levelNumber, long seed) {
        // Minimal valid JSON definition used as fallback before generator outputs detailed layout
        return String.format(
            "{\"levelNumber\":%d,\"seed\":%d,\"width\":1600,\"height\":600,\"tileSize\":32," +
            "\"environment\":\"%s\",\"spawnX\":64,\"spawnY\":420,\"exit\":{\"x\":1500,\"y\":420,\"width\":40,\"height\":60}," +
            "\"hoops\":[{\"id\":\"h1\",\"x\":320,\"y\":360,\"radius\":24,\"collected\":false}," +
            "{\"id\":\"h2\",\"x\":640,\"y\":300,\"radius\":24,\"collected\":false}," +
            "{\"id\":\"h3\",\"x\":960,\"y\":360,\"radius\":24,\"collected\":false}," +
            "{\"id\":\"h4\",\"x\":1280,\"y\":320,\"radius\":24,\"collected\":false}]," +
            "\"platforms\":[{\"x\":0,\"y\":480,\"width\":1600,\"height\":120,\"type\":\"solid\"}]," +
            "\"hazards\":[],\"interactive\":[]}",
            levelNumber, seed, selectEnvironmentForLevel(levelNumber)
        );
    }
}
