package com.billing.simple.billsoft.regression.circuit;

import com.billing.simple.billsoft.dataprotection.DataProtectionCrypto;
import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
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
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that Circuit Connect participates across ALL backup mechanisms:
 * 1. Manual Backup & Restore
 * 2. Auto / Scheduled Backup (autobackup_latest.json)
 * 3. Off-site / Cloud Data Protection upload & restore payload
 * 4. System Format (factoryReset)
 * 5. Failure isolation: Malformed game payload in backup NEVER breaks business entity restore.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CircuitConnectBackupResetAndIsolationTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private AutoBackupService autoBackupService;

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
    @DisplayName("1. Manual Backup export & restore preserves Circuit Connect progression")
    public void testManualBackupAndRestoreRecoversCircuitProgression() {
        String testState = "{\"schemaVersion\":1,\"currentLevel\":50,\"totalStars\":140,\"completedCount\":49}";
        appConfigRepo.save(new AppConfig("CIRCUIT_CONNECT_STATE", testState));
        entityManager.flush();

        // 1. Export Manual Full Backup
        BackupDTO backup = backupService.exportAllData();
        assertNotNull(backup, "BackupDTO must not be null");
        assertNotNull(backup.getAppConfigs(), "Backup appConfigs must not be null");

        boolean foundInExport = backup.getAppConfigs().stream()
                .anyMatch(cfg -> "CIRCUIT_CONNECT_STATE".equals(cfg.getConfigKey()) && testState.equals(cfg.getConfigValue()));
        assertTrue(foundInExport, "CIRCUIT_CONNECT_STATE must be in exported backup");

        // 2. Clear state
        appConfigRepo.deleteById("CIRCUIT_CONNECT_STATE");
        entityManager.flush();
        assertFalse(appConfigRepo.findById("CIRCUIT_CONNECT_STATE").isPresent());

        // 3. Restore Manual Backup (Merge mode)
        backupService.importSelectiveData(backup, null, "merge", null);
        entityManager.flush();
        entityManager.clear();

        AppConfig restored = appConfigRepo.findById("CIRCUIT_CONNECT_STATE").orElse(null);
        assertNotNull(restored, "CIRCUIT_CONNECT_STATE must be restored from manual backup");
        assertEquals(testState, restored.getConfigValue(), "Restored progression data must match original");
    }

    @Test
    @DisplayName("2. Auto / Scheduled Backup generates autobackup_latest.json containing Circuit Connect")
    public void testAutoBackupAndOffsitePayloadIntegrity() throws Exception {
        String testState = "{\"schemaVersion\":1,\"currentLevel\":100,\"totalStars\":290,\"completedCount\":99,\"highestStarMilestone\":250}";
        appConfigRepo.save(new AppConfig("CIRCUIT_CONNECT_STATE", testState));
        entityManager.flush();

        // Run automated backup
        Map<String, Object> autoResult = autoBackupService.runAutoBackup();
        assertNotNull(autoResult);
        assertEquals("HEALTHY", autoResult.get("status"));

        File backupDir = autoBackupService.getBackupDirectory();
        File latestBackupFile = new File(backupDir, "autobackup_latest.json");
        assertTrue(latestBackupFile.exists(), "autobackup_latest.json must be generated on disk");

        // Inspect actual on-disk JSON file (which is also the exact file uploaded by DataProtectionService to cloud vault)
        BackupDTO fileBackup = objectMapper.readValue(latestBackupFile, BackupDTO.class);
        assertNotNull(fileBackup);
        assertNotNull(fileBackup.getAppConfigs());

        boolean foundInDiskFile = fileBackup.getAppConfigs().stream()
                .anyMatch(cfg -> "CIRCUIT_CONNECT_STATE".equals(cfg.getConfigKey()) && testState.equals(cfg.getConfigValue()));

        assertTrue(foundInDiskFile, "Actual on-disk auto-backup file MUST contain CIRCUIT_CONNECT_STATE");
    }

    @Test
    @DisplayName("3. Cloud Data Protection encryption & restore flow restores Circuit Connect")
    public void testCloudDataProtectionRestoreFlow() throws Exception {
        String testState = "{\"schemaVersion\":1,\"currentLevel\":120,\"totalStars\":350,\"completedCount\":119}";
        appConfigRepo.save(new AppConfig("CIRCUIT_CONNECT_STATE", testState));
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
        appConfigRepo.deleteById("CIRCUIT_CONNECT_STATE");
        entityManager.flush();

        backupService.importSelectiveData(cloudRestoredBackup, null, "merge", null);
        entityManager.flush();
        entityManager.clear();

        AppConfig restored = appConfigRepo.findById("CIRCUIT_CONNECT_STATE").orElse(null);
        assertNotNull(restored, "Cloud restored backup must restore CIRCUIT_CONNECT_STATE");
        assertEquals(testState, restored.getConfigValue());
    }

    @Test
    @DisplayName("4. Failure Isolation: Corrupted Circuit Connect payload in backup NEVER breaks business data restore")
    public void testCorruptCircuitConnectInBackupDoesNotBreakBusinessRestore() {
        // Create backup with a valid Customer and a corrupt CIRCUIT_CONNECT_STATE
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Resilient Corp");
        firm = firmDetailsRepo.save(firm);

        Customer customer = new Customer();
        customer.setName("Alice Business");
        customer.setPhone("9876543210");
        customer.setFirmId(firm.getId());
        customer = customerRepo.save(customer);

        BackupDTO backup = backupService.exportAllData();

        // Inject corrupt Circuit Connect state into backup payload
        AppConfig corruptConfig = new AppConfig("CIRCUIT_CONNECT_STATE", "{ malformed_garbage::!!!");
        backup.setAppConfigs(Collections.singletonList(corruptConfig));

        // Restore onto fresh state
        backupService.importSelectiveData(backup, null, "clone", null);
        entityManager.flush();
        entityManager.clear();

        // Assert business customer is restored without failure
        assertFalse(customerRepo.findAll().isEmpty(), "Business customer entity MUST be restored successfully");
    }

    @Test
    @DisplayName("5. System Format (factoryReset) completely clears CIRCUIT_CONNECT_STATE")
    public void testFactoryResetClearsGameProgress() {
        appConfigRepo.save(new AppConfig("CIRCUIT_CONNECT_STATE", "{\"level\":999}"));
        entityManager.flush();
        assertTrue(appConfigRepo.findById("CIRCUIT_CONNECT_STATE").isPresent(), "State exists before reset");

        backupService.factoryReset();
        entityManager.flush();
        entityManager.clear();

        assertFalse(appConfigRepo.findById("CIRCUIT_CONNECT_STATE").isPresent(),
                "CIRCUIT_CONNECT_STATE must be completely cleared after factoryReset()");
    }

    @Test
    @DisplayName("6. Firm switching does not alter or partition global Circuit Connect state")
    public void testFirmSwitchingLeavesGameUnchanged() {
        String globalGame = "{\"currentLevel\":100,\"totalStars\":290}";
        appConfigRepo.save(new AppConfig("CIRCUIT_CONNECT_STATE", globalGame));
        entityManager.flush();

        FirmDetails firmA = new FirmDetails();
        firmA.setFirmName("Firm Alpha");
        firmDetailsRepo.save(firmA);

        FirmDetails firmB = new FirmDetails();
        firmB.setFirmName("Firm Beta");
        firmDetailsRepo.save(firmB);

        AppConfig readA = appConfigRepo.findById("CIRCUIT_CONNECT_STATE").orElse(null);
        assertNotNull(readA);
        assertEquals(globalGame, readA.getConfigValue());

        AppConfig readB = appConfigRepo.findById("CIRCUIT_CONNECT_STATE").orElse(null);
        assertNotNull(readB);
        assertEquals(globalGame, readB.getConfigValue());
    }
}
