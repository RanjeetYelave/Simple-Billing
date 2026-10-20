package com.billing.simple.billsoft.forecast;

import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderItem;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import com.billing.simple.billsoft.forecast.config.ForecastModuleProperties;
import com.billing.simple.billsoft.forecast.entity.ForecastProduct;
import com.billing.simple.billsoft.forecast.entity.ProductDemandForecast;
import com.billing.simple.billsoft.forecast.entity.ProductRestockRecommendation;
import com.billing.simple.billsoft.forecast.entity.ForecastModelRun;
import com.billing.simple.billsoft.forecast.repository.*;
import com.billing.simple.billsoft.forecast.service.ForecastProductConfigService;
import com.billing.simple.billsoft.forecast.service.RestockAdvisoryService;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RestockAdvisoryServiceTest {

    @Mock
    private ProductRepository productRepo;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepo;
    @Mock
    private ForecastProductConfigService configService;
    @Mock
    private ProductDemandForecastRepository forecastRepo;
    @Mock
    private ForecastModelRunRepository modelRunRepo;
    @Mock
    private ProductRestockRecommendationRepository restockRepo;
    @Mock
    private ProductSalesMetricsRepository metricsRepo;
    @Mock
    private ForecastSalesTransactionRepository transactionRepo;
    @Mock
    private ForecastModuleProperties properties;

    @InjectMocks
    private RestockAdvisoryService restockService;

    private Long firmId = 1L;
    private Long productId = 100L;
    private Long modelRunId = 10L;
    private Product product;
    private ForecastProduct forecastProduct;

    @BeforeEach
    void setUp() {
        when(properties.getDefaultLeadTimeDays()).thenReturn(7);
        when(properties.getDefaultReviewPeriodDays()).thenReturn(14);
        when(properties.getDefaultMoq()).thenReturn(BigDecimal.valueOf(1.0));
        when(properties.getDefaultPackSize()).thenReturn(BigDecimal.valueOf(1.0));
        when(properties.getDefaultSafetyStockZ()).thenReturn(1.65);

        product = new Product();
        product.setId(productId);
        product.setName("Test Product");
        product.setStockQuantity(BigDecimal.valueOf(2.0));
        product.setCostPrice(BigDecimal.valueOf(50.0));
        product.setPrice(BigDecimal.valueOf(80.0));
        product.setItemType("GOODS");

        forecastProduct = new ForecastProduct();
        forecastProduct.setFirmId(firmId);
        forecastProduct.setProductId(productId);
        forecastProduct.setLeadTimeDays(7);
        forecastProduct.setReviewPeriodDays(14);
        forecastProduct.setMinimumOrderQty(BigDecimal.valueOf(5.0));
        forecastProduct.setPackSize(BigDecimal.valueOf(1.0));

        when(configService.getOrCreateConfig(firmId, productId)).thenReturn(forecastProduct);
        when(productRepo.findByIdAndFirmId(productId, firmId)).thenReturn(Optional.of(product));
        when(purchaseOrderRepo.findByFirmIdAndStatusOrderByPoDateDescIdDesc(eq(firmId), any())).thenReturn(Collections.emptyList());
        when(restockRepo.findTopByFirmIdAndProductIdAndRecommendationStatusOrderByGeneratedAtDesc(anyLong(), anyLong(), anyString()))
                .thenReturn(Optional.empty());
        when(restockRepo.save(any(ProductRestockRecommendation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private List<ProductDemandForecast> buildForecasts(int days, double dailyDemand) {
        List<ProductDemandForecast> list = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            list.add(ProductDemandForecast.builder()
                    .firmId(firmId)
                    .productId(productId)
                    .modelRunId(modelRunId)
                    .forecastDate(LocalDate.now().plusDays(i + 1))
                    .predictedDemand(BigDecimal.valueOf(dailyDemand))
                    .confidenceScore(BigDecimal.valueOf(85.0))
                    .confidenceLevel("HIGH")
                    .build());
        }
        return list;
    }

    @Test
    @DisplayName("Safety Stock Calculation: Z * RMSE * sqrt(LeadTime) for history >= 14 days")
    void testApprovedSafetyStockFormula() {
        when(forecastRepo.findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(firmId, productId, modelRunId))
                .thenReturn(buildForecasts(21, 5.0));

        ForecastModelRun modelRun = ForecastModelRun.builder()
                .id(modelRunId)
                .firmId(firmId)
                .productId(productId)
                .trainingDays(30)
                .rmse(BigDecimal.valueOf(2.0))
                .build();
        when(modelRunRepo.findById(modelRunId)).thenReturn(Optional.of(modelRun));

        ProductRestockRecommendation rec = restockService.calculateRecommendation(firmId, productId, modelRunId);

        // Expected SS = 1.65 * 2.0 * sqrt(7) = 3.3 * 2.64575 = 8.731
        assertNotNull(rec);
        assertEquals(8.731, rec.getSafetyStock().doubleValue(), 0.01);
    }

    @Test
    @DisplayName("Safety Stock Fallback: 0.5 * AverageDailyDemand * LeadTime for history < 14 days")
    void testSafetyStockShortHistoryFallback() {
        when(forecastRepo.findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(firmId, productId, modelRunId))
                .thenReturn(buildForecasts(21, 4.0));

        ForecastModelRun modelRun = ForecastModelRun.builder()
                .id(modelRunId)
                .firmId(firmId)
                .productId(productId)
                .trainingDays(7) // < 14 days
                .rmse(BigDecimal.valueOf(1.0))
                .build();
        when(modelRunRepo.findById(modelRunId)).thenReturn(Optional.of(modelRun));

        ProductRestockRecommendation rec = restockService.calculateRecommendation(firmId, productId, modelRunId);

        // leadTimeDemand = 7 * 4 = 28.0; avgDaily = 4.0; SS = 0.5 * 4.0 * 7 = 14.000
        assertNotNull(rec);
        assertEquals(14.000, rec.getSafetyStock().doubleValue(), 0.01);
    }

    @Test
    @DisplayName("Safety Stock Override: explicit manual override takes highest priority")
    void testSafetyStockOverrideTakesPriority() {
        forecastProduct.setSafetyStockOverride(new BigDecimal("25.000"));

        when(forecastRepo.findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(firmId, productId, modelRunId))
                .thenReturn(buildForecasts(21, 5.0));

        ForecastModelRun modelRun = ForecastModelRun.builder()
                .id(modelRunId)
                .firmId(firmId)
                .productId(productId)
                .trainingDays(60)
                .rmse(BigDecimal.valueOf(3.0))
                .build();
        when(modelRunRepo.findById(modelRunId)).thenReturn(Optional.of(modelRun));

        ProductRestockRecommendation rec = restockService.calculateRecommendation(firmId, productId, modelRunId);

        assertNotNull(rec);
        assertEquals(new BigDecimal("25.000"), rec.getSafetyStock());
    }

    @Test
    @DisplayName("Incoming ISSUED PO increases inventory position and reduces recommended order quantity")
    void testIncomingIssuedPoReducesRestockQuantity() {
        when(forecastRepo.findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(firmId, productId, modelRunId))
                .thenReturn(buildForecasts(21, 5.0));

        ForecastModelRun modelRun = ForecastModelRun.builder()
                .id(modelRunId)
                .firmId(firmId)
                .productId(productId)
                .trainingDays(30)
                .rmse(BigDecimal.valueOf(2.0))
                .build();
        when(modelRunRepo.findById(modelRunId)).thenReturn(Optional.of(modelRun));

        // Mock 1 ISSUED PO with 50 units
        PurchaseOrder po = new PurchaseOrder();
        po.setStatus(PurchaseOrderStatus.ISSUED);
        PurchaseOrderItem it = new PurchaseOrderItem();
        it.setProductId(productId);
        it.setQuantity(new BigDecimal("50.000"));
        po.setItems(List.of(it));

        when(purchaseOrderRepo.findByFirmIdAndStatusOrderByPoDateDescIdDesc(eq(firmId), eq(PurchaseOrderStatus.ISSUED)))
                .thenReturn(List.of(po));

        ProductRestockRecommendation rec = restockService.calculateRecommendation(firmId, productId, modelRunId);

        assertNotNull(rec);
        assertEquals(new BigDecimal("50.000"), rec.getIncomingStock());
        // Target stock is ~113.731. Net inventory position is 2.0 (current) + 50.0 (incoming) = 52.0. Shortfall is ~61.731
        assertTrue(rec.getRecommendedOrderQty().doubleValue() < 65.0);
    }

    @Test
    @DisplayName("MOQ and Pack Size Combined Constraints: snaps up to MOQ floor then to Pack Size ceiling")
    void testMoqAndPackSizeCombinedConstraints() {
        forecastProduct.setMinimumOrderQty(new BigDecimal("20.000")); // MOQ = 20
        forecastProduct.setPackSize(new BigDecimal("12.000"));        // Pack size = 12 (dozen)

        // Demand over 21 days is 0.5/day -> 10.5 total. SS = 1.65 * 1 * sqrt(7) = 4.365. Target = 14.865.
        // Current stock = 10. Shortfall = 4.865.
        // MOQ snaps 4.865 -> 20.000. Pack size snaps 20 -> ceiling(20/12)*12 = 24.000.
        product.setStockQuantity(new BigDecimal("10.000"));

        when(forecastRepo.findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(firmId, productId, modelRunId))
                .thenReturn(buildForecasts(21, 0.5));

        ForecastModelRun modelRun = ForecastModelRun.builder()
                .id(modelRunId)
                .firmId(firmId)
                .productId(productId)
                .trainingDays(30)
                .rmse(BigDecimal.valueOf(1.0))
                .build();
        when(modelRunRepo.findById(modelRunId)).thenReturn(Optional.of(modelRun));

        ProductRestockRecommendation rec = restockService.calculateRecommendation(firmId, productId, modelRunId);

        assertNotNull(rec);
        assertEquals(new BigDecimal("20.000"), rec.getMoqAdjustedRecommendation());
        assertEquals(new BigDecimal("24.000"), rec.getRecommendedOrderQty());
    }

    @Test
    @DisplayName("Urgency Status: OUT_OF_STOCK when stock <= 0, HEALTHY when current stock exceeds target")
    void testUrgencyStatusScenarios() {
        // 1. OUT_OF_STOCK
        product.setStockQuantity(BigDecimal.ZERO);
        when(forecastRepo.findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(firmId, productId, modelRunId))
                .thenReturn(buildForecasts(21, 5.0));
        ForecastModelRun modelRun = ForecastModelRun.builder()
                .id(modelRunId)
                .trainingDays(30)
                .rmse(BigDecimal.valueOf(2.0))
                .build();
        when(modelRunRepo.findById(modelRunId)).thenReturn(Optional.of(modelRun));

        ProductRestockRecommendation recOos = restockService.calculateRecommendation(firmId, productId, modelRunId);
        assertEquals("OUT_OF_STOCK", recOos.getUrgencyStatus());

        // 2. HEALTHY (excess stock)
        product.setStockQuantity(new BigDecimal("500.000"));
        ProductRestockRecommendation recHealthy = restockService.calculateRecommendation(firmId, productId, modelRunId);
        assertEquals("HEALTHY", recHealthy.getUrgencyStatus());
        assertEquals(BigDecimal.ZERO, recHealthy.getRecommendedOrderQty());
    }

    @Test
    @DisplayName("Planning Horizon Demand: Sums lead time demand and review period demand over H = L + R")
    void testPlanningHorizonLeadAndReviewDemand() {
        forecastProduct.setLeadTimeDays(5);
        forecastProduct.setReviewPeriodDays(10);
        // Horizon H = 5 + 10 = 15 days. Daily demand = 2.0.
        // Lead time demand (5 days) = 10.0. Review period demand (10 days) = 20.0. Total = 30.0.
        when(forecastRepo.findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(firmId, productId, modelRunId))
                .thenReturn(buildForecasts(15, 2.0));

        forecastProduct.setSafetyStockOverride(new BigDecimal("5.000"));
        // Target = 30.0 + 5.0 = 35.0. Current stock = 12.0. Incoming = 0.
        // Inventory Position = 12.0. Raw Shortfall = 23.0. Stockout on day 6 (> lead time 5d -> REORDER_NEEDED).
        product.setStockQuantity(new BigDecimal("12.000"));

        ProductRestockRecommendation rec = restockService.calculateRecommendation(firmId, productId, modelRunId);

        assertNotNull(rec);
        assertEquals(new BigDecimal("10.0"), rec.getLeadTimeDemand());
        assertEquals(new BigDecimal("20.0"), rec.getReviewPeriodDemand());
        assertEquals(new BigDecimal("30.0"), rec.getTotalHorizonDemand());
        assertEquals(new BigDecimal("5.000"), rec.getSafetyStock());
        assertEquals(new BigDecimal("35.000"), rec.getTargetStock());
        assertEquals(new BigDecimal("23.000"), rec.getRawRecommendation());
        assertEquals(new BigDecimal("23.000"), rec.getRecommendedOrderQty());
        assertEquals("REORDER_NEEDED", rec.getUrgencyStatus());
    }

    @Test
    @DisplayName("Dual Signal: Forecast dominates when Forecast > Threshold requirement")
    void testDualSignalForecastDominates() {
        // Stock = 3, Min = 5 -> Threshold requirement = 2
        // Forecast target produces forecast shortfall = 10
        product.setStockQuantity(new BigDecimal("3.000"));
        product.setMinStockLevel(BigDecimal.valueOf(5));
        forecastProduct.setMinimumOrderQty(BigDecimal.ONE);
        forecastProduct.setPackSize(BigDecimal.ONE);
        forecastProduct.setSafetyStockOverride(BigDecimal.ZERO);
        forecastProduct.setLeadTimeDays(5);
        forecastProduct.setReviewPeriodDays(5); // 10 days horizon

        // Daily demand 1.3 -> 10 days demand = 13.0. Target = 13.0. Stock = 3.0. Forecast shortfall = 10.0
        when(forecastRepo.findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(firmId, productId, modelRunId))
                .thenReturn(buildForecasts(10, 1.3));

        ProductRestockRecommendation rec = restockService.calculateRecommendation(firmId, productId, modelRunId);
        assertNotNull(rec);
        assertEquals(new BigDecimal("10.000"), rec.getRecommendedOrderQty());
        assertEquals("FORECAST", restockService.deriveRecommendationReason(new BigDecimal("10.000"), new BigDecimal("2.000")));
    }

    @Test
    @DisplayName("Dual Signal: Minimum Stock dominates when Threshold > Forecast requirement")
    void testDualSignalMinStockDominates() {
        // Stock = 3, Min = 10 -> Threshold requirement = 7
        // Forecast target produces forecast shortfall = 4
        product.setStockQuantity(new BigDecimal("3.000"));
        product.setMinStockLevel(BigDecimal.valueOf(10));
        forecastProduct.setMinimumOrderQty(BigDecimal.ONE);
        forecastProduct.setPackSize(BigDecimal.ONE);
        forecastProduct.setSafetyStockOverride(BigDecimal.ZERO);
        forecastProduct.setLeadTimeDays(5);
        forecastProduct.setReviewPeriodDays(5);

        // Daily demand 0.7 -> 10 days demand = 7.0. Target = 7.0. Stock = 3.0. Forecast shortfall = 4.0
        when(forecastRepo.findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(firmId, productId, modelRunId))
                .thenReturn(buildForecasts(10, 0.7));

        ProductRestockRecommendation rec = restockService.calculateRecommendation(firmId, productId, modelRunId);
        assertNotNull(rec);
        // Combined raw recommendation takes max(4, 7) = 7
        assertEquals(new BigDecimal("7.000"), rec.getRecommendedOrderQty());
        assertEquals("MINIMUM_STOCK", restockService.deriveRecommendationReason(new BigDecimal("4.000"), new BigDecimal("7.000")));
    }

    @Test
    @DisplayName("Dual Signal: No forecast history but below minimum stock is actionable")
    void testDualSignalNoForecastHistoryBelowMinStock() {
        // Stock = 2, Min = 10, Forecast = 0 -> Threshold = 8, Final = 8
        product.setStockQuantity(new BigDecimal("2.000"));
        product.setMinStockLevel(BigDecimal.valueOf(10));
        forecastProduct.setMinimumOrderQty(BigDecimal.ONE);
        forecastProduct.setPackSize(BigDecimal.ONE);

        // Empty forecast points
        when(forecastRepo.findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(firmId, productId, modelRunId))
                .thenReturn(Collections.emptyList());

        ProductRestockRecommendation rec = restockService.calculateRecommendation(firmId, productId, modelRunId);
        assertNotNull(rec);
        assertEquals(new BigDecimal("8.000"), rec.getRecommendedOrderQty());
        assertEquals("MINIMUM_STOCK", restockService.deriveRecommendationReason(BigDecimal.ZERO, new BigDecimal("8.000")));
    }

    @Test
    @DisplayName("Dual Signal: Incoming ISSUED PO satisfies minimum stock requirement")
    void testDualSignalIncomingPoSatisfiesMinStock() {
        // Stock = 3, Incoming ISSUED PO = 7, Min = 10 -> Net = 10, Threshold = 0
        product.setStockQuantity(new BigDecimal("3.000"));
        product.setMinStockLevel(BigDecimal.valueOf(10));
        forecastProduct.setMinimumOrderQty(BigDecimal.ONE);
        forecastProduct.setPackSize(BigDecimal.ONE);

        PurchaseOrder po = new PurchaseOrder();
        po.setStatus(PurchaseOrderStatus.ISSUED);
        PurchaseOrderItem it = new PurchaseOrderItem();
        it.setProductId(productId);
        it.setQuantity(new BigDecimal("7.000"));
        po.setItems(List.of(it));

        when(purchaseOrderRepo.findByFirmIdAndStatusOrderByPoDateDescIdDesc(eq(firmId), eq(PurchaseOrderStatus.ISSUED)))
                .thenReturn(List.of(po));
        when(forecastRepo.findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(firmId, productId, modelRunId))
                .thenReturn(Collections.emptyList());

        ProductRestockRecommendation rec = restockService.calculateRecommendation(firmId, productId, modelRunId);
        assertNotNull(rec);
        assertEquals(BigDecimal.ZERO, rec.getRecommendedOrderQty());
        assertEquals("HEALTHY", rec.getUrgencyStatus());
    }

    @Test
    @DisplayName("Dual Signal: Both equal produces BOTH reason")
    void testDualSignalBothEqual() {
        assertEquals("BOTH", restockService.deriveRecommendationReason(new BigDecimal("5.000"), new BigDecimal("5.000")));
    }

    @Test
    @DisplayName("Dual Signal: Both zero produces HEALTHY reason")
    void testDualSignalBothZeroHealthy() {
        assertEquals("HEALTHY", restockService.deriveRecommendationReason(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Duplicate Prevention Lifecycle: Actionable -> Create Issued PO -> Recalculate -> Non-Actionable -> Simulate Reload/Restart -> Non-Actionable -> Receive PO -> Non-Actionable")
    void testDuplicatePreventionFullLifecycle() {
        // Step 1: Initial state (Stock = 2, Min = 10, Target = 15)
        product.setStockQuantity(new BigDecimal("2.000"));
        product.setMinStockLevel(new BigDecimal("10.000"));
        forecastProduct.setMinimumOrderQty(BigDecimal.ONE);
        forecastProduct.setPackSize(BigDecimal.ONE);

        ProductDemandForecast f1 = new ProductDemandForecast();
        f1.setPredictedDemand(new BigDecimal("13.000"));
        f1.setForecastDate(LocalDate.now().plusDays(1));
        when(forecastRepo.findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(firmId, productId, modelRunId))
                .thenReturn(List.of(f1));

        // Initial recommendation: Shortfall = 17.500 (targetStock 19.500 - currentStock 2.000)
        ProductRestockRecommendation rec1 = restockService.calculateRecommendation(firmId, productId, modelRunId);
        assertNotNull(rec1);
        assertTrue(rec1.getRecommendedOrderQty().compareTo(BigDecimal.ZERO) > 0, "Initial recommendation must be actionable");
        assertEquals(new BigDecimal("17.500"), rec1.getRecommendedOrderQty());

        // Step 2: PO Created with Status = ISSUED
        PurchaseOrder po = new PurchaseOrder();
        po.setId(999L);
        po.setFirmId(firmId);
        po.setStatus(PurchaseOrderStatus.ISSUED);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setProductId(productId);
        item.setQuantity(new BigDecimal("17.500"));
        po.setItems(List.of(item));

        when(purchaseOrderRepo.findByFirmIdAndStatusOrderByPoDateDescIdDesc(eq(firmId), eq(PurchaseOrderStatus.ISSUED)))
                .thenReturn(List.of(po));

        // Step 3: Immediate recalculation after PO creation
        ProductRestockRecommendation rec2 = restockService.calculateRecommendation(firmId, productId, modelRunId);
        assertNotNull(rec2);
        assertEquals(0, rec2.getRecommendedOrderQty().compareTo(BigDecimal.ZERO), "Recommended quantity must drop to 0 once PO is issued");
        assertEquals("HEALTHY", rec2.getUrgencyStatus(), "Item must become non-actionable / HEALTHY");

        // Step 4: Simulate Page Reload / Tab Switch / Restart (re-querying database state)
        ProductRestockRecommendation rec3 = restockService.calculateRecommendation(firmId, productId, modelRunId);
        assertNotNull(rec3);
        assertEquals(new BigDecimal("17.500"), rec3.getIncomingStock(), "Authoritative incoming stock on recommendation must be 17.500 from persisted PO");
        assertEquals(0, rec3.getRecommendedOrderQty().compareTo(BigDecimal.ZERO), "Remains non-actionable after simulated reload/restart");

        // Step 5: PO is RECEIVED (Physical stock increases to 19.500, Issued PO list is now empty)
        product.setStockQuantity(new BigDecimal("19.500"));
        when(purchaseOrderRepo.findByFirmIdAndStatusOrderByPoDateDescIdDesc(eq(firmId), eq(PurchaseOrderStatus.ISSUED)))
                .thenReturn(Collections.emptyList()); // No longer in ISSUED state

        ProductRestockRecommendation rec4 = restockService.calculateRecommendation(firmId, productId, modelRunId);
        assertEquals(0, rec4.getRecommendedOrderQty().compareTo(BigDecimal.ZERO), "Remains healthy and non-actionable after receiving PO");
        assertEquals("HEALTHY", rec4.getUrgencyStatus());
    }
}
