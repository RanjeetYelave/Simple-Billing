package com.billing.simple.billsoft.regression.bounce;

import com.billing.simple.billsoft.bounce.controllers.BounceController;
import com.billing.simple.billsoft.bounce.dto.CompleteLevelRequest;
import com.billing.simple.billsoft.bounce.dto.NewCampaignRequest;
import com.billing.simple.billsoft.bounce.dto.SaveProgressRequest;
import com.billing.simple.billsoft.bounce.dto.UpdateSettingsRequest;
import com.billing.simple.billsoft.bounce.entities.BounceCampaign;
import com.billing.simple.billsoft.bounce.entities.BounceProgress;
import com.billing.simple.billsoft.bounce.entities.BounceSettings;
import com.billing.simple.billsoft.bounce.repositories.BounceCampaignRepository;
import com.billing.simple.billsoft.bounce.repositories.BounceLevelRecordRepository;
import com.billing.simple.billsoft.bounce.repositories.BounceLevelRepository;
import com.billing.simple.billsoft.bounce.repositories.BounceProgressRepository;
import com.billing.simple.billsoft.bounce.repositories.BounceSettingsRepository;
import com.billing.simple.billsoft.bounce.services.BouncePersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Nokia Bounce H2 Persistence & API Lifecycle Tests")
public class BouncePersistenceAndBackupTest {

    @Autowired
    private BouncePersistenceService bounceService;

    @Autowired
    private BounceController bounceController;

    @Autowired
    private BounceCampaignRepository campaignRepo;

    @Autowired
    private BounceLevelRepository levelRepo;

    @Autowired
    private BounceProgressRepository progressRepo;

    @Autowired
    private BounceLevelRecordRepository recordRepo;

    @Autowired
    private BounceSettingsRepository settingsRepo;

    @BeforeEach
    public void setup() {
        progressRepo.deleteAll();
        levelRepo.deleteAll();
        campaignRepo.deleteAll();
        recordRepo.deleteAll();
        settingsRepo.deleteAll();
    }

    @Test
    @DisplayName("1. Load state initializes clean H2 campaign, level 1, progress and settings")
    public void testLoadGameStateInitializesCleanState() {
        var response = bounceService.loadGameState();
        assertNotNull(response);
        assertNotNull(response.getCampaign());
        assertEquals("ACTIVE", response.getCampaign().getCampaignStatus());
        assertEquals(1, response.getCampaign().getCurrentLevelNumber());

        assertNotNull(response.getCurrentLevel());
        assertEquals(1, response.getCurrentLevel().getLevelNumber());

        assertNotNull(response.getProgress());
        assertEquals(3, response.getProgress().getRemainingLives());
        assertEquals("NORMAL", response.getProgress().getBallState());

        assertNotNull(response.getSettings());
        assertFalse(response.getSettings().isMuted());
    }

    @Test
    @DisplayName("2. Save progress snapshot updates H2 progress atomically")
    public void testSaveProgressSnapshot() {
        var state = bounceService.loadGameState();
        Long campId = state.getCampaign().getId();

        SaveProgressRequest req = new SaveProgressRequest();
        req.setCampaignId(campId);
        req.setLevelNumber(1);
        req.setPlayerX(320.5);
        req.setPlayerY(410.2);
        req.setPlayerVx(120.0);
        req.setPlayerVy(-150.0);
        req.setBallState("BIG");
        req.setRemainingLives(2);
        req.setCurrentScore(1500);
        req.setGameplayTimeSeconds(45.5);
        req.setCheckpointId("cp_hz_1");
        req.setSnapshotVersion(2);

        BounceProgress saved = bounceService.saveProgress(req);
        assertNotNull(saved);
        assertEquals(320.5, saved.getPlayerX(), 0.001);
        assertEquals(410.2, saved.getPlayerY(), 0.001);
        assertEquals("BIG", saved.getBallState());
        assertEquals(2, saved.getRemainingLives());
        assertEquals(1500, saved.getCurrentScore());
        assertEquals("cp_hz_1", saved.getCheckpointId());

        // Verify retrieval via loadGameState
        var reloaded = bounceService.loadGameState();
        assertEquals("BIG", reloaded.getProgress().getBallState());
        assertEquals(1500, reloaded.getProgress().getCurrentScore());
    }

    @Test
    @DisplayName("3. Complete level updates record and advances campaign to next level")
    public void testCompleteLevelAdvancesCampaign() {
        var state = bounceService.loadGameState();
        Long campId = state.getCampaign().getId();

        CompleteLevelRequest compReq = new CompleteLevelRequest();
        compReq.setCampaignId(campId);
        compReq.setLevelNumber(1);
        compReq.setCompletionTimeSeconds(38.2);
        compReq.setScore(4500);
        compReq.setHoopsCollected(6);
        compReq.setTotalHoops(6);

        var advancedState = bounceService.completeLevel(compReq);
        assertNotNull(advancedState);
        assertEquals(2, advancedState.getCampaign().getCurrentLevelNumber(), "Campaign must advance to Level 2");
        assertEquals(2, advancedState.getCurrentLevel().getLevelNumber(), "Current level must now be Level 2");

        // Verify record created in Hall of Records
        var records = bounceService.getRecords();
        assertFalse(records.isEmpty());
        var r1 = records.get(0);
        assertEquals(1, r1.getLevelNumber());
        assertEquals(4500, r1.getBestScore());
        assertEquals(38.2, r1.getBestTimeSeconds(), 0.001);
    }

    @Test
    @DisplayName("4. Reset campaign preserves level records while resetting progress to Level 1")
    public void testResetCampaignPreservesRecords() {
        var state = bounceService.loadGameState();
        Long campId = state.getCampaign().getId();

        // Complete level 1
        CompleteLevelRequest compReq = new CompleteLevelRequest();
        compReq.setCampaignId(campId);
        compReq.setLevelNumber(1);
        compReq.setCompletionTimeSeconds(25.0);
        compReq.setScore(5000);
        compReq.setHoopsCollected(5);
        compReq.setTotalHoops(5);
        bounceService.completeLevel(compReq);

        // Now reset campaign
        var resetState = bounceService.resetCampaign();
        assertNotNull(resetState);
        assertEquals(1, resetState.getCampaign().getCurrentLevelNumber(), "Campaign must be back to Level 1");
        assertEquals("ACTIVE", resetState.getCampaign().getCampaignStatus());

        // Records must remain preserved
        var records = bounceService.getRecords();
        assertFalse(records.isEmpty(), "Records must be preserved across reset");
        assertEquals(1, records.get(0).getLevelNumber());
        assertEquals(5000, records.get(0).getBestScore());
    }

    @Test
    @DisplayName("5. Update settings persists audio volumes and mute status")
    public void testUpdateSettings() {
        UpdateSettingsRequest req = new UpdateSettingsRequest();
        req.setMusicVolume(0.3);
        req.setSoundVolume(0.9);
        req.setMuted(true);

        BounceSettings updated = bounceService.updateSettings(req);
        assertNotNull(updated);
        assertEquals(0.3, updated.getMusicVolume(), 0.001);
        assertEquals(0.9, updated.getSoundVolume(), 0.001);
        assertTrue(updated.isMuted());

        // Verify through controller endpoint
        var stateRes = bounceController.getState();
        assertEquals(0.3, stateRes.getBody().getSettings().getMusicVolume(), 0.001);
        assertTrue(stateRes.getBody().getSettings().isMuted());
    }

    @Test
    @DisplayName("6. Deterministic level replay retrieves identical stored level")
    public void testDeterministicLevelReplay() {
        var state = bounceService.loadGameState();
        int lvlNum = state.getCurrentLevel().getLevelNumber();

        var retrievedOpt = bounceService.getStoredLevel(lvlNum);
        assertTrue(retrievedOpt.isPresent());
        assertEquals(state.getCurrentLevel().getSeed(), retrievedOpt.get().getSeed());
        assertEquals(state.getCurrentLevel().getLevelDefinition(), retrievedOpt.get().getLevelDefinition());
    }
}
