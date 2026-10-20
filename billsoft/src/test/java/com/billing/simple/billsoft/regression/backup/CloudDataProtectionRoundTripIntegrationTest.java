package com.billing.simple.billsoft.regression.backup;

import com.billing.simple.billsoft.dataprotection.DataProtectionCrypto;
import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.SavedItem;
import com.billing.simple.billsoft.forecast.entity.ForecastProduct;
import com.billing.simple.billsoft.forecast.repository.ForecastProductRepository;
import com.billing.simple.billsoft.recovery.DiagnosticBackupOrchestrator;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.SavedItemRepository;
import com.billing.simple.billsoft.service.AutoBackupService;
import com.billing.simple.billsoft.service.BackupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("regression")
@Tag("integration")
@DisplayName("Group 2 Cross-Flow: Cloud and Data Protection Backup Round-Trip Integration Test")
@Transactional
public class CloudDataProtectionRoundTripIntegrationTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private AutoBackupService autoBackupService;

    @Autowired
    private DiagnosticBackupOrchestrator diagnosticOrchestrator;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private SavedItemRepository savedItemRepo;

    @Autowired
    private ForecastProductRepository forecastProductRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("All 4 backup mechanisms (Diagnostics, Auto, Manual, Encrypted Cloud) serialize the uniform BackupDTO contract")
    void testAllBackupMechanismsAndEncryptedRoundTrip() throws Exception {
        // 1. Seed business entities
        FirmDetails f = new FirmDetails();
        f.setFirmName("Data Protection Firm");
        f.setGstin("27AABCD1234D1Z8");
        FirmDetails firm = firmDetailsRepo.save(f);

        Product prod = productRepo.save(Product.builder()
                .firmId(firm.getId())
                .name("Protected Product")
                .sku("PROT-001")
                .price(BigDecimal.valueOf(200.0))
                .stockQuantity(BigDecimal.valueOf(15.0))
                .inInventory(true)
                .build());

        savedItemRepo.save(SavedItem.builder()
                .firmId(firm.getId())
                .name("Protected Saved Item")
                .normalizedName("protected saved item")
                .sellingPrice(BigDecimal.valueOf(190.0))
                .build());

        forecastProductRepo.save(ForecastProduct.builder()
                .firmId(firm.getId())
                .productId(prod.getId())
                .forecastEnabled(true)
                .leadTimeDays(5)
                .build());

        // Flow 1: Diagnostics backup
        Map<String, Object> diagResult = diagnosticOrchestrator.createJsonSnapshot();
        assertThat(diagResult.get("success")).isEqualTo(true);
        assertThat(diagResult.get("type")).isEqualTo("JSON_SNAPSHOT");

        // Flow 2: Auto backup
        Map<String, Object> autoResult = autoBackupService.runAutoBackup();
        assertThat(autoResult.get("status")).isEqualTo("HEALTHY");

        // Flow 3: Manual user backup
        BackupDTO manualBackup = backupService.exportAllData();
        assertThat(manualBackup).isNotNull();
        assertThat(manualBackup.getProducts()).isNotEmpty();
        assertThat(manualBackup.getSavedItems()).isNotEmpty();
        assertThat(manualBackup.getForecastProducts()).isNotEmpty();

        // Flow 4: Encrypted Cloud backup round-trip
        DataProtectionCrypto crypto = new DataProtectionCrypto();
        SecretKey secretKey = crypto.deriveKeyFromLicenseId("LIC-TEST-XYZ-12345");

        byte[] rawJsonBytes = objectMapper.writeValueAsBytes(manualBackup);
        byte[] encryptedEnvelope = crypto.encryptBackup(rawJsonBytes, secretKey);
        assertThat(encryptedEnvelope.length).isGreaterThan(rawJsonBytes.length);

        // Decrypt
        byte[] decryptedJsonBytes = crypto.decryptBackup(encryptedEnvelope, secretKey);
        BackupDTO decryptedBackup = objectMapper.readValue(decryptedJsonBytes, BackupDTO.class);

        assertThat(decryptedBackup.getProducts()).hasSameSizeAs(manualBackup.getProducts());
        assertThat(decryptedBackup.getSavedItems()).hasSameSizeAs(manualBackup.getSavedItems());
        assertThat(decryptedBackup.getForecastProducts()).hasSameSizeAs(manualBackup.getForecastProducts());
        assertThat(decryptedBackup.getProducts().stream().anyMatch(p -> "Protected Product".equals(p.getName()))).isTrue();

        // End-to-end restore into database from decrypted BackupDTO
        java.util.List<FirmDetails> restoredFirms = backupService.importSelectiveData(decryptedBackup, java.util.Set.of(firm.getId()), "clone", null);
        assertThat(restoredFirms).isNotEmpty();
        Long newFirmId = restoredFirms.get(0).getId();

        // Verify database persistence of restored entities
        assertThat(productRepo.findByFirmId(newFirmId)).anyMatch(p -> "Protected Product".equals(p.getName()));
        assertThat(savedItemRepo.findByFirmId(newFirmId)).anyMatch(si -> "protected saved item".equals(si.getNormalizedName()));
        assertThat(forecastProductRepo.findByFirmId(newFirmId)).isNotEmpty();
    }
}
