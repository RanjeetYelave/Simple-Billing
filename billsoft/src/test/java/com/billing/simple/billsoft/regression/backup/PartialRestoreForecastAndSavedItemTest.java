package com.billing.simple.billsoft.regression.backup;

import com.billing.simple.billsoft.dto.BackupDTO;
import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.SavedItem;
import com.billing.simple.billsoft.forecast.entity.ForecastProduct;
import com.billing.simple.billsoft.forecast.repository.ForecastProductRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.SavedItemRepository;
import com.billing.simple.billsoft.service.BackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("regression")
@Tag("integration")
@DisplayName("AUDIT-004: Partial Restore Forecast and SavedItem Test")
@Transactional
public class PartialRestoreForecastAndSavedItemTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private SavedItemRepository savedItemRepo;

    @Autowired
    private ForecastProductRepository forecastProductRepo;

    private FirmDetails firm;
    private Product product;

    @BeforeEach
    void setUp() {
        FirmDetails f = new FirmDetails();
        f.setFirmName("Partial Restore Test Firm");
        f.setGstin("27AABCS9876S1Z3");
        firm = firmDetailsRepo.save(f);

        product = productRepo.save(Product.builder()
                .firmId(firm.getId())
                .name("Forecasted Widget")
                .sku("WIDGET-001")
                .price(BigDecimal.valueOf(150.0))
                .stockQuantity(BigDecimal.valueOf(30.0))
                .inInventory(true)
                .build());

        savedItemRepo.save(SavedItem.builder()
                .firmId(firm.getId())
                .name("Master Saved Widget")
                .normalizedName("master saved widget")
                .sellingPrice(BigDecimal.valueOf(140.0))
                .unit("pcs")
                .usageCount(15)
                .isArchived(false)
                .canonicalProductId(product.getId())
                .build());

        forecastProductRepo.save(ForecastProduct.builder()
                .firmId(firm.getId())
                .productId(product.getId())
                .forecastEnabled(true)
                .leadTimeDays(14)
                .reviewPeriodDays(30)
                .safetyStockOverride(BigDecimal.valueOf(10.0))
                .modelOverride("CROSTON")
                .build());
    }

    @Test
    @DisplayName("BackupDTO contains SavedItems and ForecastProducts, and restore faithfully remaps them into new firm")
    void testBackupAndRestoreSavedItemsAndForecastProducts() {
        BackupDTO backup = backupService.exportData(firm.getId());

        assertThat(backup.getSavedItems()).hasSize(1);
        SavedItem exportedSi = backup.getSavedItems().get(0);
        assertThat(exportedSi.getName()).isEqualTo("Master Saved Widget");
        assertThat(exportedSi.getNormalizedName()).isEqualTo("master saved widget");
        assertThat(exportedSi.getCanonicalProductId()).isEqualTo(product.getId());

        assertThat(backup.getForecastProducts()).hasSize(1);
        ForecastProduct exportedFp = backup.getForecastProducts().get(0);
        assertThat(exportedFp.getProductId()).isEqualTo(product.getId());
        assertThat(exportedFp.getLeadTimeDays()).isEqualTo(14);
        assertThat(exportedFp.getModelOverride()).isEqualTo("CROSTON");

        // Clone restore
        List<FirmDetails> restoredFirms = backupService.importSelectiveData(backup, Set.of(firm.getId()), "clone", null);
        assertThat(restoredFirms).isNotEmpty();
        Long newFirmId = restoredFirms.get(0).getId();
        assertThat(newFirmId).isNotEqualTo(firm.getId());

        // Check restored Product
        Product newProduct = productRepo.findByFirmId(newFirmId).stream()
                .filter(p -> "WIDGET-001".equals(p.getSku())).findFirst().orElseThrow();

        // Check restored SavedItem with remapped canonicalProductId
        List<SavedItem> restoredSavedItems = savedItemRepo.findByFirmId(newFirmId);
        assertThat(restoredSavedItems).hasSize(1);
        SavedItem restoredSi = restoredSavedItems.get(0);
        assertThat(restoredSi.getNormalizedName()).isEqualTo("master saved widget");
        assertThat(restoredSi.getCanonicalProductId()).isEqualTo(newProduct.getId());

        // Check restored ForecastProduct with remapped productId
        ForecastProduct restoredFp = forecastProductRepo.findByFirmIdAndProductId(newFirmId, newProduct.getId()).orElse(null);
        assertThat(restoredFp).isNotNull();
        assertThat(restoredFp.getLeadTimeDays()).isEqualTo(14);
        assertThat(restoredFp.getReviewPeriodDays()).isEqualTo(30);
        assertThat(restoredFp.getSafetyStockOverride()).isEqualByComparingTo(BigDecimal.valueOf(10.0));
        assertThat(restoredFp.getModelOverride()).isEqualTo("CROSTON");
    }
}
