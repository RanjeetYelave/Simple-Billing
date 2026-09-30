package com.billing.simple.billsoft.regression.snake;

import com.billing.simple.billsoft.dataprotection.DataProtectionCrypto;
import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.dtos.ApiDiagnosticsResponse;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.service.ApiDiagnosticsService;
import com.billing.simple.billsoft.service.AutoBackupService;
import com.billing.simple.billsoft.service.BackupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that Snake Classic participates across all persistence, diagnostics,
 * and backup mechanisms while remaining 100% sandboxed from billing functionality.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class SnakePersistenceAndBackupTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private AutoBackupService autoBackupService;

    @Autowired
    private ApiDiagnosticsService diagnosticsService;

    @Autowired
    private AppConfigRepository appConfigRepo;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    @DisplayName("1. AppConfig persists and recovers Snake Classic progression state")
    public void testSnakePersistence() {
        String testState = "{\"schemaVersion\":1,\"stateRevision\":12,\"currentLevel\":15,\"completedCount\":14,\"totalStars\":42,\"highestStarMilestone\":0,\"totalFruitsEaten\":98,\"longestSnake\":16,\"totalPlayTimeSeconds\":3800,\"bestStreak\":8,\"currentStreak\":4}";
        appConfigRepo.save(new AppConfig("SNAKE_GAME_STATE", testState));

        AppConfig loaded = appConfigRepo.findById("SNAKE_GAME_STATE").orElse(null);
        assertNotNull(loaded);
        assertEquals(testState, loaded.getConfigValue());
    }

    @Test
    @DisplayName("2. Manual Backup export & restore preserves Snake Classic progression")
    public void testManualBackupAndRestoreRecoversSnakeProgression() {
        String testState = "{\"schemaVersion\":1,\"stateRevision\":25,\"currentLevel\":30,\"completedCount\":29,\"totalStars\":85,\"highestStarMilestone\":1,\"totalFruitsEaten\":250,\"longestSnake\":24,\"totalPlayTimeSeconds\":7200,\"bestStreak\":12,\"currentStreak\":12}";
        appConfigRepo.save(new AppConfig("SNAKE_GAME_STATE", testState));
        entityManager.flush();

        // 1. Export Manual Full Backup
        BackupDTO backup = backupService.exportAllData();
        assertNotNull(backup, "BackupDTO must not be null");
        assertNotNull(backup.getAppConfigs(), "Backup appConfigs must not be null");

        boolean foundInExport = backup.getAppConfigs().stream()
                .anyMatch(cfg -> "SNAKE_GAME_STATE".equals(cfg.getConfigKey()) && testState.equals(cfg.getConfigValue()));
        assertTrue(foundInExport, "SNAKE_GAME_STATE must be in exported backup");

        // 2. Clear state
        appConfigRepo.deleteById("SNAKE_GAME_STATE");
        entityManager.flush();
        assertFalse(appConfigRepo.findById("SNAKE_GAME_STATE").isPresent());

        // 3. Restore Manual Backup (Merge mode)
        backupService.importSelectiveData(backup, null, "merge", null);
        entityManager.flush();
        entityManager.clear();

        AppConfig restored = appConfigRepo.findById("SNAKE_GAME_STATE").orElse(null);
        assertNotNull(restored, "SNAKE_GAME_STATE must be restored from manual backup");
        assertEquals(testState, restored.getConfigValue(), "Restored progression data must match original");
    }

    @Test
    @DisplayName("3. Auto / Scheduled Backup generates autobackup_latest.json containing Snake Classic")
    public void testAutoBackupIncludesSnake() throws Exception {
        String testState = "{\"schemaVersion\":1,\"currentLevel\":50,\"totalStars\":140,\"highestStarMilestone\":2}";
        appConfigRepo.save(new AppConfig("SNAKE_GAME_STATE", testState));
        entityManager.flush();

        // Run automated backup
        Map<String, Object> autoResult = autoBackupService.runAutoBackup();
        assertNotNull(autoResult);
        assertEquals("HEALTHY", autoResult.get("status"));

        File backupDir = autoBackupService.getBackupDirectory();
        File latestBackupFile = new File(backupDir, "autobackup_latest.json");
        assertTrue(latestBackupFile.exists(), "autobackup_latest.json must be generated on disk");

        // Inspect actual on-disk JSON file
        BackupDTO fileBackup = objectMapper.readValue(latestBackupFile, BackupDTO.class);
        assertNotNull(fileBackup);
        assertNotNull(fileBackup.getAppConfigs());

        boolean foundInDiskFile = fileBackup.getAppConfigs().stream()
                .anyMatch(cfg -> "SNAKE_GAME_STATE".equals(cfg.getConfigKey()) && testState.equals(cfg.getConfigValue()));

        assertTrue(foundInDiskFile, "Actual on-disk auto-backup file MUST contain SNAKE_GAME_STATE");
    }

    @Test
    @DisplayName("4. Cloud Data Protection encryption & restore restores Snake Classic")
    public void testCloudDataProtectionRestoresSnake() throws Exception {
        String testState = "{\"schemaVersion\":1,\"currentLevel\":88,\"totalStars\":250,\"highestStarMilestone\":5}";
        appConfigRepo.save(new AppConfig("SNAKE_GAME_STATE", testState));
        entityManager.flush();

        // 1. Generate local auto backup
        autoBackupService.runAutoBackup();
        File latestBackupFile = new File(autoBackupService.getBackupDirectory(), "autobackup_latest.json");
        assertTrue(latestBackupFile.exists());

        // 2. Simulate DataProtection encryption & decryption
        DataProtectionCrypto crypto = new DataProtectionCrypto();
        javax.crypto.SecretKey testKey = crypto.deriveKeyFromLicenseId("LIC-TEST-12345");

        byte[] rawBytes = java.nio.file.Files.readAllBytes(latestBackupFile.toPath());
        byte[] encrypted = crypto.encryptBackup(rawBytes, testKey);
        byte[] decrypted = crypto.decryptBackup(encrypted, testKey);

        BackupDTO cloudRestoredBackup = objectMapper.readValue(decrypted, BackupDTO.class);
        assertNotNull(cloudRestoredBackup);

        // 3. Execute cloud restore via BackupService
        appConfigRepo.deleteById("SNAKE_GAME_STATE");
        entityManager.flush();

        backupService.importSelectiveData(cloudRestoredBackup, null, "merge", null);
        entityManager.flush();
        entityManager.clear();

        AppConfig restored = appConfigRepo.findById("SNAKE_GAME_STATE").orElse(null);
        assertNotNull(restored, "Cloud restored backup must restore SNAKE_GAME_STATE");
        assertEquals(testState, restored.getConfigValue());
    }

    @Test
    @DisplayName("5. Failure Isolation: Corrupted Snake payload in backup NEVER breaks business data restore")
    public void testCorruptSnakeInBackupDoesNotBreakBusinessRestore() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Snake Sandbox Firm");
        firm = firmDetailsRepo.save(firm);

        Customer customer = new Customer();
        customer.setName("Bob Gamer");
        customer.setPhone("9988776655");
        customer.setFirmId(firm.getId());
        customer = customerRepo.save(customer);

        BackupDTO backup = backupService.exportAllData();

        // Inject corrupt Snake state into backup payload
        AppConfig corruptConfig = new AppConfig("SNAKE_GAME_STATE", "{ malformed_garbage::!!!");
        backup.setAppConfigs(java.util.Collections.singletonList(corruptConfig));

        // Restore onto fresh state
        backupService.importSelectiveData(backup, null, "clone", null);
        entityManager.flush();
        entityManager.clear();

        List<Customer> restoredCustomers = customerRepo.findAll();
        assertFalse(restoredCustomers.isEmpty(), "Customers must be successfully restored regardless of corrupt Snake state");
    }

    @Test
    @DisplayName("6. Diagnostics suite includes Snake Classic and reports HEALTHY overall")
    public void testDiagnosticsIncludesSnake() {
        ApiDiagnosticsResponse response = diagnosticsService.runFullApiSuite(null);
        assertNotNull(response);
        assertEquals("HEALTHY", response.getOverallStatus());

        boolean hasSnakeCheck = response.getResults().stream()
                .anyMatch(ep -> ep.getName().contains("Snake Classic") || (ep.getEndpoint() != null && ep.getEndpoint().contains("SNAKE_GAME_STATE")));
        assertTrue(hasSnakeCheck, "Diagnostics suite must include Snake Classic check");
    }
}
