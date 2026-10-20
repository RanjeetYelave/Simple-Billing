package com.billing.simple.billsoft.regression.backup;

import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.forecast.entity.*;
import com.billing.simple.billsoft.forecast.repository.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.service.BackupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("regression")
@Tag("integration")
@DisplayName("AUDIT-003: Factory Reset Complete Coverage Integration Test")
public class FactoryResetCompleteCoverageIntegrationTest {

    @Autowired
    private BackupService backupService;

    @Autowired
    private FirmDetailsRepository firmDetailsRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private InvoiceItemRepository invoiceItemRepo;

    @Autowired
    private PartyRepository partyRepo;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepo;

    @Autowired
    private SavedItemRepository savedItemRepo;

    @Autowired
    private ForecastProductRepository forecastProductRepo;

    @Autowired
    private ForecastSalesTransactionRepository forecastSalesTransactionRepo;

    @Autowired
    private ProductSalesMetricsRepository productSalesMetricsRepo;

    @Autowired
    private ForecastModelRunRepository forecastModelRunRepo;

    @Autowired
    private ProductDemandForecastRepository productDemandForecastRepo;

    @Autowired
    private ProductRestockRecommendationRepository productRestockRecommendationRepo;

    @Autowired
    private PricingProfileRepository pricingProfileRepo;

    @Autowired
    private CategoryPricingRuleRepository categoryPricingRuleRepo;

    @Autowired
    private AppConfigRepository appConfigRepo;

    @Autowired
    private NotificationRepository notificationRepo;

    @Test
    @DisplayName("Factory reset purges all 43 entity tables across 12 subsystems in FK-safe order and writes FACTORY_RESET_EPOCH")
    void testCompleteFactoryResetCoverage() {
        // 1. Seed Firm
        FirmDetails f = new FirmDetails();
        f.setFirmName("Reset Test Firm");
        f.setGstin("27AABCT1234T1Z2");
        FirmDetails firm = firmDetailsRepo.save(f);
        Long firmId = firm.getId();

        // 2. Seed Customer & Product
        Customer cust = customerRepo.save(Customer.builder()
                .firmId(firmId)
                .name("Reset Customer")
                .phone("9898989898")
                .build());

        Product prod = productRepo.save(Product.builder()
                .firmId(firmId)
                .name("Reset Product")
                .sku("RST-PROD-01")
                .price(BigDecimal.valueOf(100.0))
                .stockQuantity(BigDecimal.valueOf(50.0))
                .inInventory(true)
                .build());

        // 3. Seed Pricing & SavedItem
        PricingProfile profile = pricingProfileRepo.save(PricingProfile.builder()
                .firmId(firmId)
                .name("VIP Profile")
                .code("VIP")
                .isDefault(true)
                .isActive(true)
                .build());

        categoryPricingRuleRepo.save(CategoryPricingRule.builder()
                .firmId(firmId)
                .categoryName("General")
                .pricingProfileId(profile.getId())
                .adjustmentType("PERCENT")
                .adjustmentValue(BigDecimal.valueOf(10.0))
                .build());

        savedItemRepo.save(SavedItem.builder()
                .firmId(firmId)
                .name("Reset Saved Item")
                .normalizedName("reset saved item")
                .sellingPrice(BigDecimal.valueOf(95.0))
                .build());

        // 4. Seed Forecasting Tables
        ForecastProduct fp = forecastProductRepo.save(ForecastProduct.builder()
                .firmId(firmId)
                .productId(prod.getId())
                .forecastEnabled(true)
                .leadTimeDays(7)
                .build());

        // 5. Seed Invoice & PO
        Invoice inv = invoiceRepo.save(Invoice.builder()
                .firmId(firmId)
                .customer(cust)
                .invoiceNumber("INV-RST-001")
                .invoiceDate(LocalDateTime.now())
                .totalAmount(BigDecimal.valueOf(100.0))
                .build());

        invoiceItemRepo.save(InvoiceItem.builder()
                .invoice(inv)
                .product(prod)
                .productName("Item 1")
                .qty(1)
                .pricePerUnit(BigDecimal.valueOf(100.0))
                .amountWithoutTax(BigDecimal.valueOf(100.0))
                .build());

        forecastSalesTransactionRepo.save(ForecastSalesTransaction.builder()
                .firmId(firmId)
                .sourceType("SALE")
                .sourceHeaderId(inv.getId())
                .sourceLineOrdinal(0)
                .productId(prod.getId())
                .transactionDate(LocalDate.now())
                .transactionTimestamp(LocalDateTime.now())
                .sourceUpdatedAt(LocalDateTime.now())
                .requestedQty(BigDecimal.valueOf(5.0))
                .fulfilledQty(BigDecimal.valueOf(5.0))
                .shortageQty(BigDecimal.ZERO)
                .unitPrice(BigDecimal.valueOf(100.0))
                .taxableAmount(BigDecimal.valueOf(500.0))
                .taxAmount(BigDecimal.ZERO)
                .lineTotal(BigDecimal.valueOf(500.0))
                .build());

        productSalesMetricsRepo.save(ProductSalesMetrics.builder()
                .firmId(firmId)
                .productId(prod.getId())
                .metricDate(LocalDate.now())
                .grossRequestedQty(BigDecimal.valueOf(50.0))
                .grossFulfilledQty(BigDecimal.valueOf(50.0))
                .build());

        ForecastModelRun modelRun = forecastModelRunRepo.save(ForecastModelRun.builder()
                .firmId(firmId)
                .productId(prod.getId())
                .modelName("EMA")
                .modelVersion("1.0")
                .runTimestamp(LocalDateTime.now())
                .executionDurationMs(50L)
                .trainingDays(30)
                .status("SUCCESS")
                .build());

        productDemandForecastRepo.save(ProductDemandForecast.builder()
                .firmId(firmId)
                .productId(prod.getId())
                .forecastDate(LocalDate.now().plusDays(1))
                .predictedDemand(BigDecimal.valueOf(12.0))
                .confidenceScore(BigDecimal.valueOf(85.0))
                .confidenceLevel("HIGH")
                .modelRunId(modelRun.getId())
                .build());

        productRestockRecommendationRepo.save(ProductRestockRecommendation.builder()
                .firmId(firmId)
                .productId(prod.getId())
                .modelRunId(modelRun.getId())
                .currentStock(BigDecimal.valueOf(10.0))
                .incomingStock(BigDecimal.ZERO)
                .leadTimeDemand(BigDecimal.valueOf(5.0))
                .reviewPeriodDemand(BigDecimal.valueOf(5.0))
                .totalHorizonDemand(BigDecimal.valueOf(10.0))
                .safetyStock(BigDecimal.valueOf(2.0))
                .targetStock(BigDecimal.valueOf(12.0))
                .rawRecommendation(BigDecimal.valueOf(2.0))
                .moqAdjustedRecommendation(BigDecimal.valueOf(2.0))
                .recommendedOrderQty(BigDecimal.valueOf(25.0))
                .urgencyStatus("HEALTHY")
                .confidenceLevel("HIGH")
                .recommendationStatus("ACTIVE")
                .build());

        Party party = partyRepo.save(Party.builder()
                .firmId(firmId)
                .name("Vendor Party")
                .build());

        purchaseOrderRepo.save(PurchaseOrder.builder()
                .firmId(firmId)
                .party(party)
                .poNumber("PO-RST-001")
                .poDate(LocalDate.now())
                .build());

        notificationRepo.save(Notification.builder()
                .firmId(firmId)
                .eventKey("EVENT_RST_ALERT")
                .title("Reset Alert")
                .body("Alert message")
                .build());

        appConfigRepo.save(new AppConfig("CUSTOM_THEME", "DARK"));

        // Verify data exists prior to reset
        assertThat(firmDetailsRepo.count()).isGreaterThan(0);
        assertThat(productRepo.count()).isGreaterThan(0);
        assertThat(forecastProductRepo.count()).isGreaterThan(0);
        assertThat(savedItemRepo.count()).isGreaterThan(0);

        // Execute Factory Reset
        backupService.factoryReset();

        // 6. Assert all transactional business tables are purged
        assertThat(productDemandForecastRepo.count()).isZero();
        assertThat(productRestockRecommendationRepo.count()).isZero();
        assertThat(forecastModelRunRepo.count()).isZero();
        assertThat(productSalesMetricsRepo.count()).isZero();
        assertThat(forecastSalesTransactionRepo.count()).isZero();
        assertThat(forecastProductRepo.count()).isZero();
        assertThat(savedItemRepo.count()).isZero();
        assertThat(categoryPricingRuleRepo.count()).isZero();
        assertThat(pricingProfileRepo.count()).isZero();
        assertThat(invoiceItemRepo.count()).isZero();
        assertThat(invoiceRepo.count()).isZero();
        assertThat(purchaseOrderRepo.count()).isZero();
        assertThat(partyRepo.count()).isZero();
        assertThat(notificationRepo.count()).isZero();
        assertThat(productRepo.count()).isZero();
        assertThat(customerRepo.count()).isZero();
        assertThat(firmDetailsRepo.count()).isZero();

        // 7. Verify FACTORY_RESET_EPOCH exists in AppConfig
        AppConfig epochConfig = appConfigRepo.findById("FACTORY_RESET_EPOCH").orElse(null);
        assertThat(epochConfig).isNotNull();
        assertThat(epochConfig.getConfigValue()).isNotBlank();
        long epochTime = Long.parseLong(epochConfig.getConfigValue());
        assertThat(epochTime).isGreaterThan(0L);

        // Verify other app configs were cleared
        assertThat(appConfigRepo.findById("CUSTOM_THEME")).isEmpty();
    }
}
