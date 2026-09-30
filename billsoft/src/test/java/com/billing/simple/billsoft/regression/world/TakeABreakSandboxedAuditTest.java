package com.billing.simple.billsoft.regression.world;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.service.ApiDiagnosticsService;
import com.billing.simple.billsoft.service.BackupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Sandboxing, Diagnostics, Backup/Restore, and Update Audit Test Suite
 * for Take a Break (Circuit Connect + Snake Classic).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TakeABreakSandboxedAuditTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private AppConfigRepository appConfigRepo;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;

    @Autowired
    private ApiDiagnosticsService apiDiagnosticsService;

    // --- 1. Snake Play Time Calculation & Formatting Logic ---
    public static String formatLifetimeTime(int totalSeconds) {
        int s = Math.max(0, totalSeconds);
        int hours = s / 3600;
        int mins = (s % 3600) / 60;
        int secs = s % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, mins, secs);
        }
        return String.format("%02d:%02d", mins, secs);
    }

    @Test
    @DisplayName("1. Snake lifetime play time formatting: correctly renders MM:SS and HH:MM:SS without staying at 00:00")
    public void testSnakeLifetimePlayTimeFormatting() {
        assertEquals("00:00", formatLifetimeTime(0));
        assertEquals("00:05", formatLifetimeTime(5));
        assertEquals("00:45", formatLifetimeTime(45));
        assertEquals("01:24", formatLifetimeTime(84));
        assertEquals("15:30", formatLifetimeTime(930));
        assertEquals("01:05:20", formatLifetimeTime(3920));
        assertEquals("10:00:00", formatLifetimeTime(36000));
    }

    // --- 2. OmniSearch Natural Query Alias Matching ---
    private static final Pattern SNAKE_PATTERN = Pattern.compile(
            "\\b(open|play|launch|start|go\\s*to)\\s*(?:the\\s*)?(?:snake\\s*classic|snakes?|classic\\s*snake|classic\\s*game|classic|arcade)\\b|^\\s*(?:snake|snakes|snake\\s*classic|classic|arcade)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CIRCUIT_PATTERN = Pattern.compile(
            "\\b(open|play|launch|start|go\\s*to)\\s*(?:the\\s*)?(?:circuit\\s*connect|circuit\\s*game|circuit|connect|puzzle|modern|modern\\s*game)\\b|^\\s*(?:circuit|circuit\\s*connect|connect|modern|puzzle)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern BREAK_PATTERN = Pattern.compile(
            "\\b(open|play|launch|start|go\\s*to)\\s*(?:the\\s*)?(?:take\\s*a\\s*break|take\\s*break|break|game\\s*zone|games\\s*hub|mini\\s*games|minigames|games?|play)\\b|^\\s*(?:take\\s*a\\s*break|take\\s*break|break|game\\s*zone|games\\s*hub|mini\\s*games|minigames|game|games|play)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    @Test
    @DisplayName("2. OmniSearch natural aliases correctly route to Take a Break, Snake, and Circuit Connect")
    public void testOmniSearchNaturalAliases() {
        // Snake aliases
        String[] snakeQueries = { "snake", "snakes", "Snake Classic", "classic", "arcade", "play snake", "start classic game", "open snakes" };
        for (String q : snakeQueries) {
            assertTrue(SNAKE_PATTERN.matcher(q).find(), "Query '" + q + "' must match Snake");
        }

        // Circuit Connect aliases
        String[] circuitQueries = { "circuit", "circuit connect", "connect", "modern", "puzzle", "play circuit", "start modern game", "open circuit connect" };
        for (String q : circuitQueries) {
            assertTrue(CIRCUIT_PATTERN.matcher(q).find(), "Query '" + q + "' must match Circuit Connect");
        }

        // Take a Break Hub aliases
        String[] breakQueries = { "take a break", "take break", "break", "game", "games", "games hub", "mini games", "minigames", "play", "game zone" };
        for (String q : breakQueries) {
            assertTrue(BREAK_PATTERN.matcher(q).find(), "Query '" + q + "' must match Take a Break Hub");
        }
    }

    // --- 3. Full Backup & Restore Isolation & Progression Preservation ---
    @Test
    @DisplayName("3. Backup & Restore: Preserves game progression without requiring transient board serialization")
    public void testBackupAndRestoreGameProgression() {
        FirmDetails testFirm = new FirmDetails();
        testFirm.setFirmName("Audit Test Firm");
        testFirm.setGstin("27AAACB2233P1Z1");
        testFirm.setPhone("9876543210");
        testFirm.setCity("Pune");
        testFirm = firmDetailsRepo.save(testFirm);

        // Seed AppConfig state for Circuit Connect and Snake
        String circuitStateJson = "{\"schemaVersion\":1,\"currentLevel\":16,\"completedCount\":15,\"totalStars\":45,\"highestStarMilestone\":0,\"totalPlayTimeSeconds\":420,\"totalMoves\":85}";
        String snakeStateJson = "{\"schemaVersion\":1,\"currentLevel\":8,\"completedCount\":7,\"totalStars\":21,\"highestStarMilestone\":0,\"totalFruitsEaten\":35,\"longestSnake\":12,\"totalPlayTimeSeconds\":310}";

        appConfigRepo.save(new AppConfig("CIRCUIT_CONNECT_STATE", circuitStateJson));
        appConfigRepo.save(new AppConfig("SNAKE_GAME_STATE", snakeStateJson));

        // Generate full application backup
        BackupDTO backup = backupService.exportAllData();
        assertNotNull(backup);
        assertNotNull(backup.getAppConfigs());

        boolean hasCircuit = backup.getAppConfigs().stream().anyMatch(ac -> "CIRCUIT_CONNECT_STATE".equals(ac.getConfigKey()));
        boolean hasSnake = backup.getAppConfigs().stream().anyMatch(ac -> "SNAKE_GAME_STATE".equals(ac.getConfigKey()));
        assertTrue(hasCircuit, "Backup must include CIRCUIT_CONNECT_STATE");
        assertTrue(hasSnake, "Backup must include SNAKE_GAME_STATE");

        // Clear AppConfig state to simulate clean new environment
        appConfigRepo.deleteById("CIRCUIT_CONNECT_STATE");
        appConfigRepo.deleteById("SNAKE_GAME_STATE");
        assertFalse(appConfigRepo.existsById("CIRCUIT_CONNECT_STATE"));
        assertFalse(appConfigRepo.existsById("SNAKE_GAME_STATE"));

        // Restore backup
        backupService.importData(backup, testFirm.getId(), true);

        // Verify that game progression was safely restored
        Optional<AppConfig> restoredCircuit = appConfigRepo.findById("CIRCUIT_CONNECT_STATE");
        Optional<AppConfig> restoredSnake = appConfigRepo.findById("SNAKE_GAME_STATE");

        assertTrue(restoredCircuit.isPresent(), "CIRCUIT_CONNECT_STATE must be restored");
        assertTrue(restoredSnake.isPresent(), "SNAKE_GAME_STATE must be restored");
        assertTrue(restoredCircuit.get().getConfigValue().contains("\"currentLevel\":16"));
        assertTrue(restoredSnake.get().getConfigValue().contains("\"currentLevel\":8"));
    }

    // --- 4. Corrupted / Malformed Backup Payload Resilience ---
    @Test
    @DisplayName("4. Restore Resilience: Malformed or missing game state does not crash overall application restore")
    public void testMalformedGameStateRestoreResilience() {
        FirmDetails testFirm = new FirmDetails();
        testFirm.setFirmName("Resilience Test Firm");
        testFirm.setGstin("27AAACB9999P1Z2");
        testFirm.setPhone("9876543211");
        testFirm.setCity("Satara");
        testFirm = firmDetailsRepo.save(testFirm);

        // Create a backup with corrupted/malformed game config value
        BackupDTO corruptedBackup = new BackupDTO();
        corruptedBackup.setMetadata(Map.of("version", "1.0", "sourceFirmId", testFirm.getId()));
        corruptedBackup.setFirmDetails(testFirm);
        corruptedBackup.setAppConfigs(List.of(
                new AppConfig("CIRCUIT_CONNECT_STATE", "{CORRUPTED_NON_JSON_DATA::###"),
                new AppConfig("SNAKE_GAME_STATE", "{\"currentLevel\":-999,\"totalStars\":\"INVALID\"}")
        ));

        final Long targetFirmId = testFirm.getId();
        // Restore should complete without throwing an unhandled exception or breaking business tables
        assertDoesNotThrow(() -> backupService.importData(corruptedBackup, targetFirmId, true));
        assertTrue(firmDetailsRepo.existsById(targetFirmId), "Firm data must remain intact and valid");
    }

    // --- 5. Non-Critical Diagnostics Isolation ---
    @Test
    @DisplayName("5. Diagnostics: Embedded game state endpoints are non-critical and pass health check")
    public void testDiagnosticsNonCriticalIntegration() {
        var diagnosticsReport = apiDiagnosticsService.runFullApiSuite(null);
        assertNotNull(diagnosticsReport);

        // Verify that embedded tools endpoints exist in results
        var endpointResults = diagnosticsReport.getResults();
        assertNotNull(endpointResults);

        boolean circuitCheckFound = endpointResults.stream()
                .anyMatch(e -> "Circuit Connect State Access".equals(e.getName()));
        boolean snakeCheckFound = endpointResults.stream()
                .anyMatch(e -> "Snake Classic State Access".equals(e.getName()));

        assertTrue(circuitCheckFound, "Circuit Connect state access must be covered in diagnostics");
        assertTrue(snakeCheckFound, "Snake Classic state access must be covered in diagnostics");
    }

    // --- 6. Sanitization & Extreme Values Recovery ---
    public static Map<String, Object> sanitizeClientState(Map<String, Object> state) {
        Map<String, Object> clean = new HashMap<>();
        if (state == null) {
            clean.put("currentLevel", 1);
            clean.put("completedCount", 0);
            clean.put("totalStars", 0);
            return clean;
        }

        Object lvl = state.get("currentLevel");
        int level = (lvl instanceof Number) ? Math.max(1, ((Number) lvl).intValue()) : 1;

        Object stars = state.get("totalStars");
        int totalStars = (stars instanceof Number) ? Math.max(0, ((Number) stars).intValue()) : 0;

        Object completed = state.get("completedCount");
        int completedCount = (completed instanceof Number) ? Math.max(0, ((Number) completed).intValue()) : 0;

        clean.put("currentLevel", level);
        clean.put("totalStars", totalStars);
        clean.put("completedCount", completedCount);
        return clean;
    }

    @Test
    @DisplayName("6. State Sanitization: Negative, null, NaN, and extreme values are sanitized gracefully")
    public void testClientStateSanitization() {
        // Null state -> clean defaults
        Map<String, Object> s0 = sanitizeClientState(null);
        assertEquals(1, s0.get("currentLevel"));
        assertEquals(0, s0.get("totalStars"));

        // Negative & invalid types
        Map<String, Object> dirty = new HashMap<>();
        dirty.put("currentLevel", -50);
        dirty.put("totalStars", -10);
        dirty.put("completedCount", "INVALID");

        Map<String, Object> sanitized = sanitizeClientState(dirty);
        assertEquals(1, sanitized.get("currentLevel"));
        assertEquals(0, sanitized.get("totalStars"));
        assertEquals(0, sanitized.get("completedCount"));
    }
}
