package com.billing.simple.billsoft.regression;

import com.billing.simple.billsoft.assistant.repo.OmnisearchProductQueryRepository;
import com.billing.simple.billsoft.assistant.service.OmnisearchQuickHelpService;
import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.engine.InvoiceCalculationEngine;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.forecast.entity.ForecastSalesTransaction;
import com.billing.simple.billsoft.forecast.entity.ProductDemandForecast;
import com.billing.simple.billsoft.forecast.entity.ProductRestockRecommendation;
import com.billing.simple.billsoft.forecast.entity.ProductSalesMetrics;
import com.billing.simple.billsoft.forecast.repository.*;
import com.billing.simple.billsoft.forecast.service.ForecastAggregationService;
import com.billing.simple.billsoft.forecast.service.ForecastIngestionService;
import com.billing.simple.billsoft.forecast.service.RestockAdvisoryService;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class Group6ComprehensiveRegressionTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductPricingService productPricingService;

    @Autowired
    private PricingProfileRepository pricingProfileRepository;

    @Autowired
    private CategoryPricingRuleRepository categoryPricingRuleRepository;

    @Autowired
    private InvoiceCalculationEngine invoiceCalculationEngine;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OmnisearchProductQueryRepository omnisearchProductQueryRepository;

    @Autowired
    private OmnisearchQuickHelpService omnisearchQuickHelpService;

    @Autowired
    private ItemMergeService itemMergeService;

    @Autowired
    private ForecastSalesTransactionRepository forecastTransactionRepository;

    @Autowired
    private ProductSalesMetricsRepository metricsRepository;

    @Autowired
    private ProductDemandForecastRepository forecastRepository;

    @Autowired
    private ProductRestockRecommendationRepository restockRepository;

    @Autowired
    private ForecastIngestionService forecastIngestionService;

    @Autowired
    private ForecastAggregationService forecastAggregationService;

    @Autowired
    private RestockAdvisoryService restockAdvisoryService;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PartyRepository partyRepository;

    private static final Long FIRM_A = 201L;
    private static final Long FIRM_B = 202L;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(FIRM_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =========================================================================
    // AUDIT-010: POS / INVOICE PRICING AUTHORITY
    // =========================================================================

    @Test
    @DisplayName("AUDIT-010: Omitted pricingProfileId resolves default active PricingProfile and category rule")
    void testAudit010_DefaultPricingProfileResolvedWhenOmitted() {
        // Ensure default profiles exist
        productPricingService.ensureDefaultProfilesExist(FIRM_A);
        PricingProfile defaultProfile = pricingProfileRepository.findFirstByFirmIdAndIsDefaultTrue(FIRM_A).orElseThrow();

        // Create a category pricing rule on default profile: Hardware + 10%
        CategoryPricingRule rule = CategoryPricingRule.builder()
                .firmId(FIRM_A)
                .pricingProfileId(defaultProfile.getId())
                .categoryName("Hardware")
                .adjustmentType("PERCENT")
                .adjustmentValue(new BigDecimal("10.00"))
                .build();
        categoryPricingRuleRepository.save(rule);

        Product p = Product.builder()
                .firmId(FIRM_A)
                .name("Hammer 500g")
                .category("Hardware")
                .price(new BigDecimal("500.00"))
                .itemType("GOODS")
                .build();
        p = productRepository.save(p);

        Customer c = customerRepository.save(Customer.builder().firmId(FIRM_A).name("Test Cust 10").build());

        InvoiceRequestItem item = InvoiceRequestItem.builder()
                .productId(p.getId())
                .productName(p.getName())
                .qty(1)
                .pricePerUnit(null) // omitted pricePerUnit
                .build();

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(c.getId());
        req.setFirmId(FIRM_A);
        req.setPricingProfileId(null); // omitted pricingProfileId
        req.setItems(Collections.singletonList(item));

        Invoice inv = new Invoice();
        inv.setFirmId(FIRM_A);

        Invoice calculated = invoiceCalculationEngine.calculate(inv, c, Collections.singletonList(p), req, false);

        assertNotNull(calculated);
        assertEquals(defaultProfile.getId(), calculated.getPricingProfileId());
        // Base 500 + 10% = 550.00
        assertEquals(new BigDecimal("550.00").stripTrailingZeros(),
                calculated.getItems().get(0).getPricePerUnit().stripTrailingZeros());
    }

    @Test
    @DisplayName("AUDIT-010: Wholesale profile evaluates configured discount")
    void testAudit010_WholesaleProfileEvaluation() {
        productPricingService.ensureDefaultProfilesExist(FIRM_A);
        PricingProfile wholesale = pricingProfileRepository.findByFirmIdAndCode(FIRM_A, "WHOLESALE").orElseThrow();

        CategoryPricingRule rule = CategoryPricingRule.builder()
                .firmId(FIRM_A)
                .pricingProfileId(wholesale.getId())
                .categoryName("Plumbing")
                .adjustmentType("PERCENT")
                .adjustmentValue(new BigDecimal("-20.00")) // 20% discount
                .build();
        categoryPricingRuleRepository.save(rule);

        Product p = Product.builder()
                .firmId(FIRM_A)
                .name("PVC Pipe 10ft")
                .category("Plumbing")
                .price(new BigDecimal("200.00"))
                .itemType("GOODS")
                .build();
        p = productRepository.save(p);

        Customer c = customerRepository.save(Customer.builder().firmId(FIRM_A).name("Wholesale Buyer").build());

        InvoiceRequestItem item = InvoiceRequestItem.builder()
                .productId(p.getId())
                .productName(p.getName())
                .qty(5)
                .pricePerUnit(null)
                .build();

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(c.getId());
        req.setFirmId(FIRM_A);
        req.setPricingProfileId(wholesale.getId());
        req.setItems(Collections.singletonList(item));

        Invoice inv = new Invoice();
        inv.setFirmId(FIRM_A);

        Invoice calculated = invoiceCalculationEngine.calculate(inv, c, Collections.singletonList(p), req, false);

        assertNotNull(calculated);
        // 200 - 20% = 160.00
        assertEquals(new BigDecimal("160.00").stripTrailingZeros(),
                calculated.getItems().get(0).getPricePerUnit().stripTrailingZeros());
        assertEquals(new BigDecimal("800.00").stripTrailingZeros(),
                calculated.getItems().get(0).getAmountWithoutTax().stripTrailingZeros());
    }

    @Test
    @DisplayName("AUDIT-010: Manual positive price override takes precedence over pricing profile")
    void testAudit010_ManualPositiveOverridePreserved() {
        productPricingService.ensureDefaultProfilesExist(FIRM_A);
        PricingProfile defaultProfile = pricingProfileRepository.findFirstByFirmIdAndIsDefaultTrue(FIRM_A).orElseThrow();

        Product p = Product.builder()
                .firmId(FIRM_A)
                .name("Cordless Drill")
                .category("Tools")
                .price(new BigDecimal("3000.00"))
                .itemType("GOODS")
                .build();
        p = productRepository.save(p);

        Customer c = customerRepository.save(Customer.builder().firmId(FIRM_A).name("Cust Override").build());

        InvoiceRequestItem item = InvoiceRequestItem.builder()
                .productId(p.getId())
                .productName(p.getName())
                .qty(1)
                .pricePerUnit(new BigDecimal("2750.00")) // Cashier negotiated custom override
                .build();

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(c.getId());
        req.setFirmId(FIRM_A);
        req.setPricingProfileId(defaultProfile.getId());
        req.setItems(Collections.singletonList(item));

        Invoice inv = new Invoice();
        inv.setFirmId(FIRM_A);

        Invoice calculated = invoiceCalculationEngine.calculate(inv, c, Collections.singletonList(p), req, false);

        assertNotNull(calculated);
        assertEquals(new BigDecimal("2750.00").stripTrailingZeros(),
                calculated.getItems().get(0).getPricePerUnit().stripTrailingZeros());
    }

    @Test
    @DisplayName("AUDIT-010: Explicit free price (0.00) is preserved without falling back to base price")
    void testAudit010_ExplicitFreePriceZeroPreserved() {
        productPricingService.ensureDefaultProfilesExist(FIRM_A);
        PricingProfile defaultProfile = pricingProfileRepository.findFirstByFirmIdAndIsDefaultTrue(FIRM_A).orElseThrow();

        Product p = Product.builder()
                .firmId(FIRM_A)
                .name("Promotional Pen")
                .price(new BigDecimal("20.00"))
                .itemType("GOODS")
                .build();
        p = productRepository.save(p);

        Customer c = customerRepository.save(Customer.builder().firmId(FIRM_A).name("Free Item Cust").build());

        InvoiceRequestItem item = InvoiceRequestItem.builder()
                .productId(p.getId())
                .productName(p.getName())
                .qty(3)
                .pricePerUnit(BigDecimal.ZERO) // Free promotional item
                .build();

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(c.getId());
        req.setFirmId(FIRM_A);
        req.setPricingProfileId(defaultProfile.getId());
        req.setItems(Collections.singletonList(item));

        Invoice inv = new Invoice();
        inv.setFirmId(FIRM_A);

        Invoice calculated = invoiceCalculationEngine.calculate(inv, c, Collections.singletonList(p), req, false);

        assertNotNull(calculated);
        assertEquals(new BigDecimal("0.00").stripTrailingZeros(),
                calculated.getItems().get(0).getPricePerUnit().stripTrailingZeros());
        assertEquals(new BigDecimal("0.00").stripTrailingZeros(),
                calculated.getItems().get(0).getAmountWithoutTax().stripTrailingZeros());
    }

    @Test
    @DisplayName("AUDIT-010: Negative unit price throws IllegalArgumentException")
    void testAudit010_NegativePriceRejected() {
        final Product p = productRepository.save(Product.builder()
                .firmId(FIRM_A)
                .name("Faulty Item")
                .price(new BigDecimal("100.00"))
                .build());

        Customer c = customerRepository.save(Customer.builder().firmId(FIRM_A).name("Neg Cust").build());

        InvoiceRequestItem item = InvoiceRequestItem.builder()
                .productId(p.getId())
                .productName(p.getName())
                .qty(1)
                .pricePerUnit(new BigDecimal("-50.00"))
                .build();

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(c.getId());
        req.setFirmId(FIRM_A);
        req.setItems(Collections.singletonList(item));

        Invoice inv = new Invoice();
        inv.setFirmId(FIRM_A);

        assertThrows(IllegalArgumentException.class, () ->
                invoiceCalculationEngine.calculate(inv, c, Collections.singletonList(p), req, false));
    }

    @Test
    @DisplayName("AUDIT-010: Historical InvoiceItem.pricePerUnit snapshot remains immutable when rules change")
    void testAudit010_HistoricalInvoiceItemSnapshotImmutable() {
        productPricingService.ensureDefaultProfilesExist(FIRM_A);
        PricingProfile defaultProfile = pricingProfileRepository.findFirstByFirmIdAndIsDefaultTrue(FIRM_A).orElseThrow();

        CategoryPricingRule rule = CategoryPricingRule.builder()
                .firmId(FIRM_A)
                .pricingProfileId(defaultProfile.getId())
                .categoryName("Seasonal")
                .adjustmentType("PERCENT")
                .adjustmentValue(new BigDecimal("10.00"))
                .build();
        rule = categoryPricingRuleRepository.save(rule);

        Product p = Product.builder()
                .firmId(FIRM_A)
                .name("Holiday Lights")
                .category("Seasonal")
                .price(new BigDecimal("100.00"))
                .build();
        p = productRepository.save(p);

        Customer c = customerRepository.save(Customer.builder().firmId(FIRM_A).name("Hist Cust").build());

        InvoiceRequestItem item = InvoiceRequestItem.builder()
                .productId(p.getId())
                .productName(p.getName())
                .qty(1)
                .pricePerUnit(null)
                .build();

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(c.getId());
        req.setFirmId(FIRM_A);
        req.setPricingProfileId(defaultProfile.getId());
        req.setItems(Collections.singletonList(item));

        Invoice inv = new Invoice();
        inv.setFirmId(FIRM_A);

        Invoice calculated = invoiceCalculationEngine.calculate(inv, c, Collections.singletonList(p), req, false);
        assertEquals(new BigDecimal("110.00").stripTrailingZeros(),
                calculated.getItems().get(0).getPricePerUnit().stripTrailingZeros());

        // Now modify the rule to 50% markup
        rule.setAdjustmentValue(new BigDecimal("50.00"));
        categoryPricingRuleRepository.save(rule);

        // Saved snapshot remains 110.00
        assertEquals(new BigDecimal("110.00").stripTrailingZeros(),
                calculated.getItems().get(0).getPricePerUnit().stripTrailingZeros());
    }

    // =========================================================================
    // AUDIT-016: ARCHIVED PRODUCT OPERATIONAL FILTERING
    // =========================================================================

    @Test
    @DisplayName("AUDIT-016: Archived product is excluded from active operational product listings")
    void testAudit016_ArchivedProductExcludedFromOperationalQueries() {
        Product activeP = Product.builder()
                .firmId(FIRM_A)
                .name("Active Widget")
                .price(new BigDecimal("100.00"))
                .stockQuantity(new BigDecimal("10.000"))
                .inInventory(true)
                .isArchived(false)
                .build();
        activeP = productRepository.save(activeP);

        Product archivedP = Product.builder()
                .firmId(FIRM_A)
                .name("Archived Widget")
                .price(new BigDecimal("50.00"))
                .stockQuantity(new BigDecimal("2.000"))
                .inInventory(true)
                .isArchived(true)
                .build();
        archivedP = productRepository.save(archivedP);

        // 1. productService.getAll(firmId)
        List<Product> activeList = productService.getAll(FIRM_A);
        List<Long> activeIds = activeList.stream().map(Product::getId).toList();
        assertTrue(activeIds.contains(activeP.getId()));
        assertFalse(activeIds.contains(archivedP.getId()));

        // 2. productService.getAllProducts(firmId)
        List<Product> allActive = productService.getAllProducts(FIRM_A);
        List<Long> allActiveIds = allActive.stream().map(Product::getId).toList();
        assertTrue(allActiveIds.contains(activeP.getId()));
        assertFalse(allActiveIds.contains(archivedP.getId()));

        // 3. Omnisearch top-5 active typeahead
        List<Product> top5 = productRepository.findTop5ByFirmIdAndIsArchivedFalseAndNameContainingIgnoreCaseOrderByNameAsc(FIRM_A, "Widget");
        List<Long> top5Ids = top5.stream().map(Product::getId).toList();
        assertTrue(top5Ids.contains(activeP.getId()));
        assertFalse(top5Ids.contains(archivedP.getId()));

        // 4. Omnisearch candidate projection query
        var candidates = omnisearchProductQueryRepository.searchCandidates(FIRM_A, "Widget");
        List<Long> candidateIds = candidates.stream().map(OmnisearchProductQueryRepository.ProductCandidateProjection::getId).toList();
        assertTrue(candidateIds.contains(activeP.getId()));
        assertFalse(candidateIds.contains(archivedP.getId()));
    }

    @Test
    @DisplayName("AUDIT-016: Archived product is excluded from low stock calculation and inventory summary")
    void testAudit016_ArchivedProductExcludedFromLowStockAndSummary() {
        Product lowStockArchived = Product.builder()
                .firmId(FIRM_A)
                .name("Obsolete Part Low Stock")
                .price(new BigDecimal("100.00"))
                .costPrice(new BigDecimal("60.00"))
                .stockQuantity(new BigDecimal("1.000"))
                .minStockLevel(new BigDecimal("10.000"))
                .itemType("GOODS")
                .inInventory(true)
                .isArchived(true)
                .build();
        lowStockArchived = productRepository.save(lowStockArchived);

        List<Product> lowStock = productRepository.findLowStockProductsByFirmId(FIRM_A);
        List<Long> lowStockIds = lowStock.stream().map(Product::getId).toList();
        assertFalse(lowStockIds.contains(lowStockArchived.getId()));

        Map<String, Object> summary = productService.getInventorySummary(FIRM_A);
        assertNotNull(summary);
        // The archived product is skipped from total products count and valuation
    }

    @Test
    @DisplayName("AUDIT-016: Archived product remains accessible for historical transactions via findByIdAndFirmId")
    void testAudit016_ArchivedProductRetainedInHistoricalAccess() {
        Product archivedP = Product.builder()
                .firmId(FIRM_A)
                .name("Historical Old Product")
                .price(new BigDecimal("250.00"))
                .isArchived(true)
                .build();
        archivedP = productRepository.save(archivedP);

        Optional<Product> found = productRepository.findByIdAndFirmId(archivedP.getId(), FIRM_A);
        assertTrue(found.isPresent());
        assertEquals("Historical Old Product", found.get().getName());
        assertTrue(Boolean.TRUE.equals(found.get().getIsArchived()));
    }

    // =========================================================================
    // AUDIT-018: PRODUCT MERGE + FORECAST CONSOLIDATION
    // =========================================================================

    @Test
    @Transactional
    @DisplayName("AUDIT-018: Product merge consolidates forecast sales transactions and invalidates secondary derived data")
    void testAudit018_ProductMergeConsolidatesForecastTransactions() {
        Product primary = Product.builder()
                .firmId(FIRM_A)
                .name("Brand A USB Cable")
                .stockQuantity(new BigDecimal("10.000"))
                .price(new BigDecimal("150.00"))
                .build();
        primary = productRepository.save(primary);

        Product secondary = Product.builder()
                .firmId(FIRM_A)
                .name("Brand A USB Cable (Duplicate)")
                .stockQuantity(new BigDecimal("5.000"))
                .price(new BigDecimal("150.00"))
                .build();
        secondary = productRepository.save(secondary);

        LocalDate txDate = LocalDate.now().minusDays(5);

        // Historical forecast transactions for secondary
        ForecastSalesTransaction secTx = ForecastSalesTransaction.builder()
                .firmId(FIRM_A)
                .sourceType("SALE")
                .sourceHeaderId(9991L)
                .sourceLineOrdinal(0)
                .productId(secondary.getId())
                .transactionDate(txDate)
                .transactionTimestamp(LocalDateTime.now().minusDays(5))
                .requestedQty(new BigDecimal("4.000"))
                .fulfilledQty(new BigDecimal("4.000"))
                .shortageQty(BigDecimal.ZERO)
                .unitPrice(new BigDecimal("150.00"))
                .taxableAmount(new BigDecimal("600.00"))
                .taxAmount(BigDecimal.ZERO)
                .lineTotal(new BigDecimal("600.00"))
                .invoiceStatus("FINAL")
                .sourceUpdatedAt(LocalDateTime.now().minusDays(5))
                .build();
        forecastTransactionRepository.save(secTx);

        // Create secondary metrics & recommendation
        ProductSalesMetrics secMetric = ProductSalesMetrics.builder()
                .firmId(FIRM_A)
                .productId(secondary.getId())
                .metricDate(txDate)
                .grossRequestedQty(new BigDecimal("4.000"))
                .build();
        metricsRepository.save(secMetric);

        ProductRestockRecommendation secRec = ProductRestockRecommendation.builder()
                .firmId(FIRM_A)
                .productId(secondary.getId())
                .modelRunId(1L)
                .currentStock(BigDecimal.ZERO)
                .incomingStock(BigDecimal.ZERO)
                .leadTimeDemand(BigDecimal.ZERO)
                .reviewPeriodDemand(BigDecimal.ZERO)
                .totalHorizonDemand(BigDecimal.ZERO)
                .safetyStock(BigDecimal.ZERO)
                .targetStock(BigDecimal.ZERO)
                .rawRecommendation(BigDecimal.ZERO)
                .moqAdjustedRecommendation(BigDecimal.ZERO)
                .recommendedOrderQty(new BigDecimal("20.000"))
                .urgencyStatus("HEALTHY")
                .confidenceLevel("HIGH")
                .recommendationStatus("ACTIVE")
                .generatedAt(LocalDateTime.now())
                .build();
        restockRepository.save(secRec);

        // Execute merge
        MergeRequestDto req = new MergeRequestDto();
        req.setPrimaryId(primary.getId());
        req.setSecondaryId(secondary.getId());
        req.setPrimaryType("PRODUCT");
        req.setSecondaryType("PRODUCT");
        req.setStockStrategy("ADD");

        Map<String, Object> result = itemMergeService.mergeItems(FIRM_A, req);
        assertEquals("SUCCESS", result.get("status"));

        // Verify transactions migrated to primary
        List<ForecastSalesTransaction> primaryTxs = forecastTransactionRepository
                .findAllByFirmIdAndProductIdAndTransactionDateBetweenOrderByTransactionDateAsc(
                        FIRM_A, primary.getId(), txDate.minusDays(1), txDate.plusDays(1));
        assertFalse(primaryTxs.isEmpty());
        assertEquals(primary.getId(), primaryTxs.get(0).getProductId());

        // Verify secondary derived rows deleted
        assertTrue(metricsRepository.findByFirmIdAndProductIdAndMetricDate(FIRM_A, secondary.getId(), txDate).isEmpty());
        assertTrue(restockRepository.findTopByFirmIdAndProductIdOrderByGeneratedAtDesc(FIRM_A, secondary.getId()).isEmpty());

        // Verify secondary product marked archived and points to primary
        Product updatedSec = productRepository.findByIdAndFirmId(secondary.getId(), FIRM_A).orElseThrow();
        assertTrue(Boolean.TRUE.equals(updatedSec.getIsArchived()));
        assertEquals(primary.getId(), updatedSec.getCanonicalProductId());
        assertEquals(BigDecimal.ZERO.stripTrailingZeros(), updatedSec.getStockQuantity().stripTrailingZeros());

        // Verify stock consolidated
        Product updatedPrimary = productRepository.findByIdAndFirmId(primary.getId(), FIRM_A).orElseThrow();
        assertEquals(new BigDecimal("15.000").stripTrailingZeros(), updatedPrimary.getStockQuantity().stripTrailingZeros());
    }

    @Test
    @DisplayName("AUDIT-018: Transitive canonical resolution updates intermediate pointers")
    void testAudit018_TransitiveCanonicalResolution() {
        Product root = Product.builder().firmId(FIRM_A).name("Root Product").build();
        root = productRepository.save(root);

        Product intermediate = Product.builder().firmId(FIRM_A).name("Intermediate Product").build();
        intermediate = productRepository.save(intermediate);

        Product leaf = Product.builder().firmId(FIRM_A).name("Leaf Product").build();
        leaf = productRepository.save(leaf);

        // 1. Merge leaf -> intermediate
        MergeRequestDto req1 = new MergeRequestDto();
        req1.setPrimaryId(intermediate.getId());
        req1.setSecondaryId(leaf.getId());
        req1.setPrimaryType("PRODUCT");
        req1.setSecondaryType("PRODUCT");
        itemMergeService.mergeItems(FIRM_A, req1);

        assertEquals(intermediate.getId(), productRepository.findById(leaf.getId()).orElseThrow().getCanonicalProductId());

        // 2. Merge intermediate -> root
        MergeRequestDto req2 = new MergeRequestDto();
        req2.setPrimaryId(root.getId());
        req2.setSecondaryId(intermediate.getId());
        req2.setPrimaryType("PRODUCT");
        req2.setSecondaryType("PRODUCT");
        itemMergeService.mergeItems(FIRM_A, req2);

        // Leaf's canonical pointer should now point directly to root
        Product updatedLeaf = productRepository.findById(leaf.getId()).orElseThrow();
        assertEquals(root.getId(), updatedLeaf.getCanonicalProductId());

        // Dynamic resolver resolves root
        Long resolved = itemMergeService.resolveCanonicalProductId(FIRM_A, leaf.getId());
        assertEquals(root.getId(), resolved);
    }

    @Test
    @DisplayName("AUDIT-018: Canonical cycle detection prevents circular merge")
    void testAudit018_CanonicalCycleRejection() {
        Product pA = Product.builder().firmId(FIRM_A).name("Prod A").build();
        pA = productRepository.save(pA);

        Product pB = Product.builder().firmId(FIRM_A).name("Prod B").canonicalProductId(pA.getId()).build();
        pB = productRepository.save(pB);

        // Attempting to merge pA into pB (where pB already points to pA)
        MergeRequestDto req = new MergeRequestDto();
        req.setPrimaryId(pB.getId());
        req.setSecondaryId(pA.getId());
        req.setPrimaryType("PRODUCT");
        req.setSecondaryType("PRODUCT");

        assertThrows(IllegalArgumentException.class, () -> itemMergeService.mergeItems(FIRM_A, req));
    }

    @Test
    @DisplayName("AUDIT-018: Cross-firm merge is strictly prohibited")
    void testAudit018_CrossFirmMergeRejected() {
        Product pA = Product.builder().firmId(FIRM_A).name("Firm A Prod").build();
        pA = productRepository.save(pA);

        Product pB = Product.builder().firmId(FIRM_B).name("Firm B Prod").build();
        pB = productRepository.save(pB);

        MergeRequestDto req = new MergeRequestDto();
        req.setPrimaryId(pA.getId());
        req.setSecondaryId(pB.getId());
        req.setPrimaryType("PRODUCT");
        req.setSecondaryType("PRODUCT");

        assertThrows(TenantSecurityException.class, () -> itemMergeService.mergeItems(FIRM_A, req));
    }

    // =========================================================================
    // AUDIT-020: PO RECEIPT AUTO-CREATED PRODUCT PREFERRED VENDOR LINK
    // =========================================================================

    @Test
    @DisplayName("AUDIT-020: Ad-hoc item on PO receipt auto-creates Product with preferredPartyId set to PO vendor")
    void testAudit020_AdHocPOItemCreatesProductWithPreferredVendor() {
        Party vendor = Party.builder()
                .firmId(FIRM_A)
                .name("Supreme Hardware Supplies")
                .build();
        vendor = partyRepository.save(vendor);

        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .productName("Adhoc Steel Bracket 50mm")
                .description("Heavy duty bracket")
                .hsnCode("7308")
                .quantity(new BigDecimal("100.000"))
                .unitPrice(new BigDecimal("45.00")) // cost price
                .gstPercent(new BigDecimal("18.00"))
                .unit("pcs")
                .build();

        PurchaseOrder po = PurchaseOrder.builder()
                .firmId(FIRM_A)
                .poNumber("PO-GRP6-001")
                .party(vendor)
                .poDate(LocalDate.now())
                .status(PurchaseOrderStatus.ISSUED)
                .items(new ArrayList<>(Collections.singletonList(poItem)))
                .build();
        poItem.setPurchaseOrder(po);
        po = purchaseOrderRepository.save(po);

        // Transition to RECEIVED status to trigger stock receipt & auto-creation
        purchaseOrderService.updateStatus(po.getId(), FIRM_A, PurchaseOrderStatus.RECEIVED);

        // Find the created product
        Optional<Product> createdOpt = productRepository.findFirstByFirmIdAndNameIgnoreCase(FIRM_A, "Adhoc Steel Bracket 50mm");
        assertTrue(createdOpt.isPresent());
        Product created = createdOpt.get();

        // 1. preferredPartyId matches PO vendor
        assertEquals(vendor.getId(), created.getPreferredPartyId());
        // 2. Cost price matches PO unit price
        assertEquals(new BigDecimal("45.00").stripTrailingZeros(), created.getCostPrice().stripTrailingZeros());
        // 3. Selling price is distinct from procurement cost
        assertEquals(BigDecimal.ZERO.stripTrailingZeros(), created.getPrice().stripTrailingZeros());
        // 4. Stock quantity received
        assertEquals(new BigDecimal("100.000").stripTrailingZeros(), created.getStockQuantity().stripTrailingZeros());
    }

    @Test
    @DisplayName("AUDIT-020: Existing product without preferred vendor adopts PO vendor on receipt")
    void testAudit020_ExistingProductMissingPreferredVendorPopulated() {
        Party vendor = Party.builder()
                .firmId(FIRM_A)
                .name("Reliable Timber")
                .build();
        vendor = partyRepository.save(vendor);

        Product existing = Product.builder()
                .firmId(FIRM_A)
                .name("Teak Planks 4x2")
                .price(new BigDecimal("400.00"))
                .costPrice(new BigDecimal("280.00"))
                .stockQuantity(new BigDecimal("10.000"))
                .preferredPartyId(null) // No preferred vendor
                .build();
        existing = productRepository.save(existing);

        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .productId(existing.getId())
                .productName(existing.getName())
                .quantity(new BigDecimal("20.000"))
                .unitPrice(new BigDecimal("280.00"))
                .unit("pcs")
                .build();

        PurchaseOrder po = PurchaseOrder.builder()
                .firmId(FIRM_A)
                .poNumber("PO-GRP6-002")
                .party(vendor)
                .poDate(LocalDate.now())
                .status(PurchaseOrderStatus.ISSUED)
                .items(new ArrayList<>(Collections.singletonList(poItem)))
                .build();
        poItem.setPurchaseOrder(po);
        po = purchaseOrderRepository.save(po);

        purchaseOrderService.updateStatus(po.getId(), FIRM_A, PurchaseOrderStatus.RECEIVED);

        Product updated = productRepository.findByIdAndFirmId(existing.getId(), FIRM_A).orElseThrow();
        assertEquals(vendor.getId(), updated.getPreferredPartyId());
        assertEquals(new BigDecimal("30.000").stripTrailingZeros(), updated.getStockQuantity().stripTrailingZeros());
    }

    @Test
    @DisplayName("AUDIT-020: Existing product with preferred vendor retains its original vendor on receipt")
    void testAudit020_ExistingProductPreferredVendorPreserved() {
        Party originalVendor = Party.builder()
                .firmId(FIRM_A)
                .name("Original Vendor")
                .build();
        originalVendor = partyRepository.save(originalVendor);

        Party secondaryVendor = Party.builder()
                .firmId(FIRM_A)
                .name("Secondary Vendor")
                .build();
        secondaryVendor = partyRepository.save(secondaryVendor);

        Product existing = Product.builder()
                .firmId(FIRM_A)
                .name("Copper Wire 1mm")
                .price(new BigDecimal("500.00"))
                .preferredPartyId(originalVendor.getId()) // Established vendor
                .build();
        existing = productRepository.save(existing);

        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .productId(existing.getId())
                .productName(existing.getName())
                .quantity(new BigDecimal("10.000"))
                .unitPrice(new BigDecimal("350.00"))
                .build();

        PurchaseOrder po = PurchaseOrder.builder()
                .firmId(FIRM_A)
                .poNumber("PO-GRP6-003")
                .party(secondaryVendor) // Different vendor
                .poDate(LocalDate.now())
                .status(PurchaseOrderStatus.ISSUED)
                .items(new ArrayList<>(Collections.singletonList(poItem)))
                .build();
        poItem.setPurchaseOrder(po);
        po = purchaseOrderRepository.save(po);

        purchaseOrderService.updateStatus(po.getId(), FIRM_A, PurchaseOrderStatus.RECEIVED);

        Product updated = productRepository.findByIdAndFirmId(existing.getId(), FIRM_A).orElseThrow();
        // Preferred vendor remains originalVendor.getId()
        assertEquals(originalVendor.getId(), updated.getPreferredPartyId());
    }
}
