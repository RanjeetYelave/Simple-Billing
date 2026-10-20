package com.billing.simple.billsoft.regression.backup;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.AppConfig;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.AppConfigRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.service.BackupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("regression")
@Tag("integration")
@DisplayName("Group 2 Cross-Flow: Take a Break / Snake Lifecycle Safety Test")
@Transactional
public class SnakeLifecycleCrossFlowIntegrationTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private AppConfigRepository appConfigRepo;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;

    @Test
    @DisplayName("SNAKE_GAME_STATE remains global and merge restore honors stateRevision to protect progression")
    void testSnakeGameStateRevisionMerge() {
        FirmDetails f = new FirmDetails();
        f.setFirmName("Snake Firm");
        f.setGstin("27AABCS0000S1Z7");
        FirmDetails firm = firmDetailsRepo.save(f);

        // 1. Current DB has stateRevision 20
        String dbState = "{\"schemaVersion\":1,\"stateRevision\":20,\"currentLevel\":10,\"totalStars\":25}";
        appConfigRepo.save(new AppConfig("SNAKE_GAME_STATE", dbState));

        // 2. Backup with older revision 5 must NOT overwrite DB
        String olderBackupState = "{\"schemaVersion\":1,\"stateRevision\":5,\"currentLevel\":3,\"totalStars\":6}";
        BackupDTO olderBackup = new BackupDTO();
        olderBackup.setMetadata(Map.of("version", "2.0", "type", "SINGLE_FIRM", "sourceFirmId", firm.getId()));
        olderBackup.setFirmDetails(firm);
        olderBackup.setAppConfigs(List.of(new AppConfig("SNAKE_GAME_STATE", olderBackupState)));

        backupService.importSelectiveData(olderBackup, Set.of(firm.getId()), "merge", firm.getId());

        AppConfig stateAfterOlder = appConfigRepo.findById("SNAKE_GAME_STATE").orElseThrow();
        assertThat(stateAfterOlder.getConfigValue()).isEqualTo(dbState);

        // 3. Backup with newer revision 35 MUST update DB
        String newerBackupState = "{\"schemaVersion\":1,\"stateRevision\":35,\"currentLevel\":18,\"totalStars\":50}";
        BackupDTO newerBackup = new BackupDTO();
        newerBackup.setMetadata(Map.of("version", "2.0", "type", "SINGLE_FIRM", "sourceFirmId", firm.getId()));
        newerBackup.setFirmDetails(firm);
        newerBackup.setAppConfigs(List.of(new AppConfig("SNAKE_GAME_STATE", newerBackupState)));

        backupService.importSelectiveData(newerBackup, Set.of(firm.getId()), "merge", firm.getId());

        AppConfig stateAfterNewer = appConfigRepo.findById("SNAKE_GAME_STATE").orElseThrow();
        assertThat(stateAfterNewer.getConfigValue()).isEqualTo(newerBackupState);

        // 4. Verify SNAKE_GAME_STATE was not scoped to firm (no firm prefix)
        assertThat(appConfigRepo.findById("SNAKE_GAME_STATE_" + firm.getId())).isEmpty();
    }
}
