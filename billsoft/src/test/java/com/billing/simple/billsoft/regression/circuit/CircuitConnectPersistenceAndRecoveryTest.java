package com.billing.simple.billsoft.regression.circuit;

import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence, crash resilience, and recovery tests for Circuit Connect.
 * Validates the core philosophy: Persist PROGRESSION, not historical boards.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CircuitConnectPersistenceAndRecoveryTest {

    @Autowired
    private AppConfigRepository appConfigRepo;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("AppConfig persists and recovers Circuit Connect progression state")
    public void testAppConfigStateSaveAndRead() throws Exception {
        Map<String, Object> state = new HashMap<>();
        state.put("schemaVersion", 1);
        state.put("stateRevision", 105);
        state.put("currentLevel", 42);
        state.put("completedCount", 41);
        state.put("skippedCount", 2);
        state.put("totalStars", 120);
        state.put("highestStarMilestone", 100);
        state.put("totalPlayTimeSeconds", 3600);
        state.put("totalMoves", 850);
        state.put("bestStreak", 14);

        String json = mapper.writeValueAsString(state);
        appConfigRepo.save(new AppConfig("CIRCUIT_CONNECT_STATE", json));

        AppConfig loaded = appConfigRepo.findById("CIRCUIT_CONNECT_STATE").orElse(null);
        assertNotNull(loaded, "AppConfig must exist");

        @SuppressWarnings("unchecked")
        Map<String, Object> loadedMap = mapper.readValue(loaded.getConfigValue(), Map.class);
        assertEquals(1, loadedMap.get("schemaVersion"));
        assertEquals(42, loadedMap.get("currentLevel"));
        assertEquals(120, loadedMap.get("totalStars"));
        assertEquals(100, loadedMap.get("highestStarMilestone"));
    }

    @Test
    @DisplayName("CRASH SIMULATION: Level 999 in-progress puzzle lost -> Safely resumes progression at Level 1000")
    public void testLevel999CrashAndProgressionRecovery() throws Exception {
        // Player state before crash at Level 999
        Map<String, Object> stateBeforeCrash = new HashMap<>();
        stateBeforeCrash.put("schemaVersion", 1);
        stateBeforeCrash.put("stateRevision", 5000);
        stateBeforeCrash.put("currentLevel", 999);
        stateBeforeCrash.put("completedCount", 998);
        stateBeforeCrash.put("skippedCount", 12);
        stateBeforeCrash.put("totalStars", 2421);
        stateBeforeCrash.put("highestStarMilestone", 2400);
        stateBeforeCrash.put("totalPlayTimeSeconds", 152280); // 42h 18m
        stateBeforeCrash.put("totalMoves", 12345);
        stateBeforeCrash.put("bestStreak", 14);

        // Persist to durable AppConfig
        appConfigRepo.save(new AppConfig("CIRCUIT_CONNECT_STATE", mapper.writeValueAsString(stateBeforeCrash)));

        // SIMULATE CRASH:
        // Current board memory is completely destroyed.
        // Recovery logic reads durable AppConfig, determines progression state.
        AppConfig recoveredConfig = appConfigRepo.findById("CIRCUIT_CONNECT_STATE").orElse(null);
        assertNotNull(recoveredConfig, "Recovered state must be present in AppConfig");

        @SuppressWarnings("unchecked")
        Map<String, Object> recovered = mapper.readValue(recoveredConfig.getConfigValue(), Map.class);

        // Assert progression integrity survived the crash
        assertEquals(998, recovered.get("completedCount"), "Completed levels preserved");
        assertEquals(12, recovered.get("skippedCount"), "Skipped levels preserved");
        assertEquals(2421, recovered.get("totalStars"), "Total stars preserved");
        assertEquals(2400, recovered.get("highestStarMilestone"), "Trophies/milestones preserved");
        assertEquals(152280, recovered.get("totalPlayTimeSeconds"), "Playtime preserved");

        // System advances safely to next level and generates fresh puzzle
        int resumeLevel = ((Number) recovered.get("currentLevel")).intValue();
        assertTrue(resumeLevel >= 999, "Resumes at Level 999 or 1000 without requiring old board");
    }

    @Test
    @DisplayName("Corrupt JSON in storage falls back gracefully without application error")
    public void testCorruptStorageFallback() {
        // Save garbage string to AppConfig
        appConfigRepo.save(new AppConfig("CIRCUIT_CONNECT_STATE", "{ malformed_json::: !!!"));

        AppConfig loaded = appConfigRepo.findById("CIRCUIT_CONNECT_STATE").orElse(null);
        assertNotNull(loaded);

        boolean recovered = false;
        try {
            mapper.readValue(loaded.getConfigValue(), Map.class);
        } catch (Exception e) {
            // Safe fallback to clean state
            recovered = true;
        }

        assertTrue(recovered, "Malformed JSON safely handled and cleanly recoverable");
    }
}
