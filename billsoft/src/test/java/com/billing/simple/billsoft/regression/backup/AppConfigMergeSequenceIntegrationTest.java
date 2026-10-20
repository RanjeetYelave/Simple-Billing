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
@DisplayName("AUDIT-033: AppConfig Merge Sequence and Credentials Safety Integration Test")
@Transactional
public class AppConfigMergeSequenceIntegrationTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private AppConfigRepository appConfigRepo;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;

    @Test
    @DisplayName("AppConfig merge sequence counters use Math.max and sensitive credentials are never overwritten")
    void testAppConfigMergeSequenceCountersAndSensitiveCredentials() {
        FirmDetails f = new FirmDetails();
        f.setFirmName("Sequence Firm");
        f.setGstin("27AABCQ1234Q1Z5");
        FirmDetails firm = firmDetailsRepo.save(f);
        Long firmId = firm.getId();

        // Setup existing AppConfigs in database
        appConfigRepo.save(new AppConfig("LAST_INVOICE_NUM_" + firmId, "50"));
        appConfigRepo.save(new AppConfig("PO_SEQ_" + firmId, "20"));
        appConfigRepo.save(new AppConfig("GLOBAL_PASSWORD", "argon2_live_hash_secret"));
        appConfigRepo.save(new AppConfig("BACKUP_CLOUD_SECRET", "live_aws_secret_key"));
        appConfigRepo.save(new AppConfig("FACTORY_RESET_EPOCH", "9999999999"));

        // Prepare backup with conflicting/attacker values
        BackupDTO backup = new BackupDTO();
        backup.setMetadata(Map.of("version", "2.0", "type", "SINGLE_FIRM", "sourceFirmId", firmId));
        backup.setFirmDetails(firm);
        backup.setAppConfigs(List.of(
                new AppConfig("LAST_INVOICE_NUM_" + firmId, "10"), // Lower -> must stay 50
                new AppConfig("PO_SEQ_" + firmId, "85"),           // Higher -> must advance to 85
                new AppConfig("GLOBAL_PASSWORD", "malicious_overwrite_hash"),
                new AppConfig("BACKUP_CLOUD_SECRET", "stolen_secret"),
                new AppConfig("FACTORY_RESET_EPOCH", "1111111111")
        ));

        // Perform merge restore
        backupService.importSelectiveData(backup, Set.of(firmId), "merge", firmId);

        // Verify sequence counters
        AppConfig invoiceSeq = appConfigRepo.findById("LAST_INVOICE_NUM_" + firmId).orElseThrow();
        assertThat(invoiceSeq.getConfigValue()).isEqualTo("50");

        AppConfig poSeq = appConfigRepo.findById("PO_SEQ_" + firmId).orElseThrow();
        assertThat(poSeq.getConfigValue()).isEqualTo("85");

        // Verify sensitive credentials were preserved and NOT overwritten
        AppConfig pwd = appConfigRepo.findById("GLOBAL_PASSWORD").orElseThrow();
        assertThat(pwd.getConfigValue()).isEqualTo("argon2_live_hash_secret");

        AppConfig cloudSec = appConfigRepo.findById("BACKUP_CLOUD_SECRET").orElseThrow();
        assertThat(cloudSec.getConfigValue()).isEqualTo("live_aws_secret_key");

        AppConfig epoch = appConfigRepo.findById("FACTORY_RESET_EPOCH").orElseThrow();
        assertThat(epoch.getConfigValue()).isEqualTo("9999999999");
    }
}
