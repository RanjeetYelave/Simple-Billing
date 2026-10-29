package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.dtos.PricingProfileDto;
import com.billing.simple.billsoft.dtos.ProductBulkRowDto;
import com.billing.simple.billsoft.engine.InvoiceCalculationEngine;
import com.billing.simple.billsoft.entities.CategoryPricingRule;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.PricingProfile;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.StockMovement;
import com.billing.simple.billsoft.repo.CategoryPricingRuleRepository;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.PricingProfileRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repo.StockMovementRepository;
import com.billing.simple.billsoft.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import com.billing.simple.billsoft.BillsoftApplication;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = BillsoftApplication.class)
@ActiveProfiles("test")
@Transactional
public class ProductBulkAddAndPricingIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductPricingService productPricingService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceCalculationEngine calculationEngine;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private PricingProfileRepository pricingProfileRepo;

    @Autowired
    private CategoryPricingRuleRepository categoryPricingRuleRepo;

    @Autowired
    private StockMovementRepository stockMovementRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    private final Long FIRM_A = 901L;
    private final Long FIRM_B = 902L;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(FIRM_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =========================================================================
    // SECTION 1: PRODUCT BULK ADD TESTS
    // =========================================================================

    @Test
    @DisplayName("Bulk Add: Successfully imports valid goods and services with initial stock movements and inventory flags")
    void testBulkAddProducts_Success() {
        ProductBulkRowDto item1 = new ProductBulkRowDto();
        item1.setName("Industrial Gear A1");
        item1.setCategory("Mechanical");
        item1.setPrice(new BigDecimal("1500.00"));
        item1.setCostPrice(new BigDecimal("1100.00"));
        item1.setSku("GEAR-A1-001");
        item1.setBarcode("8901234001");
        item1.setHsnCode("8483");
        item1.setGstPercentage(new BigDecimal("18.00"));
        item1.setInitialStock(new BigDecimal("50.00"));
        item1.setMinStockLevel(new BigDecimal("10.00"));
        item1.setItemType("GOODS");
        item1.setInInventory(true);

        ProductBulkRowDto item2 = new ProductBulkRowDto();
        item2.setName("Gear Installation Service");
        item2.setCategory("Services");
        item2.setPrice(new BigDecimal("800.00"));
        item2.setHsnCode("9987");
        item2.setGstPercentage(new BigDecimal("18.00"));
        item2.setItemType("SERVICE");
        item2.setInInventory(false);

        List<Product> saved = productService.createBulk(Arrays.asList(item1, item2), FIRM_A);

        assertNotNull(saved);
        assertEquals(2, saved.size());

        Product savedP1 = productRepo.findByIdAndFirmId(saved.get(0).getId(), FIRM_A).orElse(null);
        assertNotNull(savedP1);
        assertEquals("Industrial Gear A1", savedP1.getName());
        assertEquals(new BigDecimal("50.00").stripTrailingZeros(), savedP1.getStockQuantity().stripTrailingZeros());
        assertTrue(savedP1.getInInventory());

        // Verify stock movement ledger entry
        List<StockMovement> movements = stockMovementRepo.findByProductIdOrderByCreatedAtDesc(savedP1.getId());
        assertFalse(movements.isEmpty());
        assertEquals("INITIAL_STOCK", movements.get(0).getMovementType());
        assertEquals(new BigDecimal("50.00").stripTrailingZeros(), movements.get(0).getQuantityChange().stripTrailingZeros());

        Product savedP2 = productRepo.findByIdAndFirmId(saved.get(1).getId(), FIRM_A).orElse(null);
        assertNotNull(savedP2);
        assertEquals("Gear Installation Service", savedP2.getName());
        assertEquals("SERVICE", savedP2.getItemType());
        assertFalse(savedP2.getInInventory());
    }

    @Test
    @DisplayName("Bulk Add: Rejects intra-batch duplicate SKU and rolls back entirely")
    void testBulkAddProducts_IntraBatchDuplicateSkuRejection() {
        ProductBulkRowDto item1 = new ProductBulkRowDto();
        item1.setName("Valve Alpha");
        item1.setSku("VALVE-DUP-01");
        item1.setPrice(new BigDecimal("100.00"));

        ProductBulkRowDto item2 = new ProductBulkRowDto();
        item2.setName("Valve Beta");
        item2.setSku("VALVE-DUP-01"); // Duplicate SKU in same batch
        item2.setPrice(new BigDecimal("200.00"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            productService.createBulk(Arrays.asList(item1, item2), FIRM_A);
        });

        assertTrue(ex.getMessage().contains("Duplicate SKU"));
    }

    @Test
    @DisplayName("Bulk Add: Rejects intra-batch duplicate Barcode and rolls back entirely")
    void testBulkAddProducts_IntraBatchDuplicateBarcodeRejection() {
        ProductBulkRowDto item1 = new ProductBulkRowDto();
        item1.setName("Bolt X1");
        item1.setBarcode("BAR-999-DUP");
        item1.setPrice(new BigDecimal("10.00"));

        ProductBulkRowDto item2 = new ProductBulkRowDto();
        item2.setName("Bolt X2");
        item2.setBarcode("BAR-999-DUP"); // Duplicate Barcode in same batch
        item2.setPrice(new BigDecimal("15.00"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            productService.createBulk(Arrays.asList(item1, item2), FIRM_A);
        });

        assertTrue(ex.getMessage().contains("Duplicate Barcode"));
    }

    @Test
    @DisplayName("Bulk Add: Rejects item matching existing database SKU in same firm")
    void testBulkAddProducts_ExistingDatabaseSkuRejection() {
        // Pre-create product in DB
        Product existing = new Product();
        existing.setName("Existing Pump");
        existing.setSku("PUMP-EX-001");
        existing.setPrice(new BigDecimal("5000.00"));
        existing.setFirmId(FIRM_A);
        productRepo.save(existing);

        ProductBulkRowDto item = new ProductBulkRowDto();
        item.setName("New Duplicate Pump");
        item.setSku("PUMP-EX-001");
        item.setPrice(new BigDecimal("5500.00"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            productService.createBulk(Collections.singletonList(item), FIRM_A);
        });

        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("Bulk Add: Multi-firm isolation allows same SKU in different firms")
    void testBulkAddProducts_MultiFirmSkuAllowed() {
        // Create in Firm A
        ProductBulkRowDto itemA = new ProductBulkRowDto();
        itemA.setName("Common Pipe");
        itemA.setSku("PIPE-COM-01");
        itemA.setPrice(new BigDecimal("250.00"));
        productService.createBulk(Collections.singletonList(itemA), FIRM_A);

        // Switch context to Firm B
        TenantContext.setCurrentFirmId(FIRM_B);
        ProductBulkRowDto itemB = new ProductBulkRowDto();
        itemB.setName("Common Pipe Firm B");
        itemB.setSku("PIPE-COM-01"); // Same SKU in Firm B
        itemB.setPrice(new BigDecimal("260.00"));

        List<Product> savedB = productService.createBulk(Collections.singletonList(itemB), FIRM_B);
        assertNotNull(savedB);
        assertEquals(1, savedB.size());
    }

    @Test
    @DisplayName("Bulk Add: Validates negative numbers, invalid GST, and blank names")
    void testBulkAddProducts_ValidationErrors() {
        // Negative Price
        ProductBulkRowDto item1 = new ProductBulkRowDto();
        item1.setName("Negative Price Item");
        item1.setPrice(new BigDecimal("-10.00"));
        assertThrows(IllegalArgumentException.class, () -> productService.createBulk(Collections.singletonList(item1), FIRM_A));

        // Negative Initial Stock
        ProductBulkRowDto item2 = new ProductBulkRowDto();
        item2.setName("Negative Stock Item");
        item2.setPrice(new BigDecimal("10.00"));
        item2.setInitialStock(new BigDecimal("-5.00"));
        assertThrows(IllegalArgumentException.class, () -> productService.createBulk(Collections.singletonList(item2), FIRM_A));

        // Invalid GST (> 100%)
        ProductBulkRowDto item3 = new ProductBulkRowDto();
        item3.setName("Invalid GST Item");
        item3.setPrice(new BigDecimal("10.00"));
        item3.setGstPercentage(new BigDecimal("150.00"));
        assertThrows(IllegalArgumentException.class, () -> productService.createBulk(Collections.singletonList(item3), FIRM_A));

        // Blank Name
        ProductBulkRowDto item4 = new ProductBulkRowDto();
        item4.setName("   ");
        item4.setPrice(new BigDecimal("10.00"));
        assertThrows(IllegalArgumentException.class, () -> productService.createBulk(Collections.singletonList(item4), FIRM_A));
    }

    // =========================================================================
    // SECTION 2: PRICING PROFILES AND CATEGORY RULES
    // =========================================================================

    @Test
    @DisplayName("Pricing Profiles: Enforces uniqueness, single default flag, and firm scoping")
    void testPricingProfiles_LifecycleAndDefaultFlag() {
        PricingProfileDto wholesale = productPricingService.createProfile(
                PricingProfileDto.builder()
                        .name("Wholesale Tier 1")
                        .code("WHOLESALE_1")
                        .description("Tier 1 wholesale rates")
                        .isDefault(false)
                        .isActive(true)
                        .build(),
                FIRM_A
        );
        assertNotNull(wholesale.getId());
        assertFalse(wholesale.getIsDefault());

        // Make wholesale default
        wholesale.setIsDefault(true);
        PricingProfileDto updated = productPricingService.updateProfile(wholesale.getId(), wholesale, FIRM_A);
        assertTrue(updated.getIsDefault());

        // Check that only 1 profile is default
        List<PricingProfileDto> profiles = productPricingService.listProfiles(FIRM_A);
        long defaultCount = profiles.stream().filter(p -> Boolean.TRUE.equals(p.getIsDefault())).count();
        assertEquals(1, defaultCount);

        // Reject duplicate code in same firm
        assertThrows(IllegalArgumentException.class, () -> {
            productPricingService.createProfile(
                    PricingProfileDto.builder()
                            .name("Wholesale Duplicate")
                            .code("WHOLESALE_1")
                            .build(),
                    FIRM_A
            );
        });
    }

    @Test
    @DisplayName("Category Pricing: Percentage and Fixed adjustments with clamp at 0.00")
    void testCategoryPricing_Adjustments() {
        PricingProfileDto vipProfile = productPricingService.createProfile(
                PricingProfileDto.builder()
                        .name("VIP Pricing")
                        .code("VIP_PRICING")
                        .isDefault(false)
                        .isActive(true)
                        .build(),
                FIRM_A
        );

        // Rule 1: Electronics +20% (Default operator +)
        categoryPricingRuleRepo.save(
                CategoryPricingRule.builder()
                        .firmId(FIRM_A)
                        .pricingProfileId(vipProfile.getId())
                        .categoryName("Electronics")
                        .operator("+")
                        .adjustmentType("PERCENT")
                        .adjustmentValue(new BigDecimal("20.00"))
                        .build()
        );

        // Rule 2: Clearance -100.00 Fixed (Legacy negative value with +)
        categoryPricingRuleRepo.save(
                CategoryPricingRule.builder()
                        .firmId(FIRM_A)
                        .pricingProfileId(vipProfile.getId())
                        .categoryName("Clearance")
                        .operator("+")
                        .adjustmentType("FIXED")
                        .adjustmentValue(new BigDecimal("-100.00"))
                        .build()
        );

        // Rule 3: Furniture with explicit Subtraction (-) operator: 15% discount
        categoryPricingRuleRepo.save(
                CategoryPricingRule.builder()
                        .firmId(FIRM_A)
                        .pricingProfileId(vipProfile.getId())
                        .categoryName("Furniture")
                        .operator("-")
                        .adjustmentType("PERCENT")
                        .adjustmentValue(new BigDecimal("15.00"))
                        .build()
        );

        // Rule 4: Machinery with Multiplication (*) operator: 1.5x fixed factor
        categoryPricingRuleRepo.save(
                CategoryPricingRule.builder()
                        .firmId(FIRM_A)
                        .pricingProfileId(vipProfile.getId())
                        .categoryName("Machinery")
                        .operator("*")
                        .adjustmentType("FIXED")
                        .adjustmentValue(new BigDecimal("1.50"))
                        .build()
        );

        // Rule 5: WholesaleBulk with Division (/) operator: divide by 2.00
        categoryPricingRuleRepo.save(
                CategoryPricingRule.builder()
                        .firmId(FIRM_A)
                        .pricingProfileId(vipProfile.getId())
                        .categoryName("WholesaleBulk")
                        .operator("/")
                        .adjustmentType("FIXED")
                        .adjustmentValue(new BigDecimal("2.00"))
                        .build()
        );

        // Rule 6: DivZeroCategory with Division (/) operator by 0 -> safe fallback to base price
        categoryPricingRuleRepo.save(
                CategoryPricingRule.builder()
                        .firmId(FIRM_A)
                        .pricingProfileId(vipProfile.getId())
                        .categoryName("DivZeroCategory")
                        .operator("/")
                        .adjustmentType("FIXED")
                        .adjustmentValue(BigDecimal.ZERO)
                        .build()
        );

        Product pElec = new Product();
        pElec.setName("Laptop Core i7");
        pElec.setCategory("Electronics");
        pElec.setPrice(new BigDecimal("1000.00"));
        pElec.setFirmId(FIRM_A);

        Product pCheap = new Product();
        pCheap.setName("Cheap Cable");
        pCheap.setCategory("Clearance");
        pCheap.setPrice(new BigDecimal("30.00"));
        pCheap.setFirmId(FIRM_A);

        Product pFurn = new Product();
        pFurn.setName("Ergonomic Chair");
        pFurn.setCategory("Furniture");
        pFurn.setPrice(new BigDecimal("2000.00"));
        pFurn.setFirmId(FIRM_A);

        Product pMach = new Product();
        pMach.setName("Drill Press");
        pMach.setCategory("Machinery");
        pMach.setPrice(new BigDecimal("500.00"));
        pMach.setFirmId(FIRM_A);

        Product pBulk = new Product();
        pBulk.setName("Steel Wire Spool");
        pBulk.setCategory("WholesaleBulk");
        pBulk.setPrice(new BigDecimal("800.00"));
        pBulk.setFirmId(FIRM_A);

        Product pZeroDiv = new Product();
        pZeroDiv.setName("Safety Mask Box");
        pZeroDiv.setCategory("DivZeroCategory");
        pZeroDiv.setPrice(new BigDecimal("150.00"));
        pZeroDiv.setFirmId(FIRM_A);

        Product pUncat = new Product();
        pUncat.setName("Uncategorized Box");
        pUncat.setPrice(new BigDecimal("50.00"));
        pUncat.setFirmId(FIRM_A);

        // Electronics (+ 20%): 1000 + 20% = 1200.00
        BigDecimal effElec = productPricingService.calculateEffectivePrice(pElec, vipProfile.getId(), FIRM_A);
        assertEquals(new BigDecimal("1200.00").stripTrailingZeros(), effElec.stripTrailingZeros());

        // Cheap Cable (+ -100): 30 - 100 = -70 -> Clamped to 0.00
        BigDecimal effCheap = productPricingService.calculateEffectivePrice(pCheap, vipProfile.getId(), FIRM_A);
        assertEquals(new BigDecimal("0.00").stripTrailingZeros(), effCheap.stripTrailingZeros());

        // Furniture (- 15%): 2000 - 15% = 1700.00
        BigDecimal effFurn = productPricingService.calculateEffectivePrice(pFurn, vipProfile.getId(), FIRM_A);
        assertEquals(new BigDecimal("1700.00").stripTrailingZeros(), effFurn.stripTrailingZeros());

        // Machinery (* 1.50): 500 * 1.50 = 750.00
        BigDecimal effMach = productPricingService.calculateEffectivePrice(pMach, vipProfile.getId(), FIRM_A);
        assertEquals(new BigDecimal("750.00").stripTrailingZeros(), effMach.stripTrailingZeros());

        // WholesaleBulk (/ 2.00): 800 / 2 = 400.00
        BigDecimal effBulk = productPricingService.calculateEffectivePrice(pBulk, vipProfile.getId(), FIRM_A);
        assertEquals(new BigDecimal("400.00").stripTrailingZeros(), effBulk.stripTrailingZeros());

        // DivZeroCategory (/ 0.00): safe fallback to base price 150.00
        BigDecimal effZeroDiv = productPricingService.calculateEffectivePrice(pZeroDiv, vipProfile.getId(), FIRM_A);
        assertEquals(new BigDecimal("150.00").stripTrailingZeros(), effZeroDiv.stripTrailingZeros());

        // Uncategorized: falls back to base price 50.00
        BigDecimal effUncat = productPricingService.calculateEffectivePrice(pUncat, vipProfile.getId(), FIRM_A);
        assertEquals(new BigDecimal("50.00").stripTrailingZeros(), effUncat.stripTrailingZeros());
    }

    // =========================================================================
    // SECTION 3: INVOICE ENGINE CALCULATION WITH PRICING PROFILES
    // =========================================================================

    @Test
    @DisplayName("Invoice Calculation: null pricePerUnit uses Pricing Profile, 0.00 is explicit, negative is rejected")
    void testInvoiceEngine_PricePerUnitSemantics() {
        PricingProfileDto contractorProfile = productPricingService.createProfile(
                PricingProfileDto.builder()
                        .name("Contractor Profile")
                        .code("CONTRACTOR_T1")
                        .isDefault(false)
                        .isActive(true)
                        .build(),
                FIRM_A
        );

        categoryPricingRuleRepo.save(
                CategoryPricingRule.builder()
                        .firmId(FIRM_A)
                        .pricingProfileId(contractorProfile.getId())
                        .categoryName("Hardware")
                        .adjustmentType("PERCENT")
                        .adjustmentValue(new BigDecimal("-15.00")) // 15% discount
                        .build()
        );

        Product tool = new Product();
        tool.setName("Drill Machine");
        tool.setCategory("Hardware");
        tool.setPrice(new BigDecimal("2000.00"));
        tool.setFirmId(FIRM_A);
        tool = productRepo.save(tool);

        Customer customer = new Customer();
        customer.setName("Acme Builders");
        customer.setFirmId(FIRM_A);
        customer = customerRepo.save(customer);

        // Item 1: pricePerUnit == null -> should resolve to 2000 - 15% = 1700.00
        InvoiceRequestItem item1 = new InvoiceRequestItem();
        item1.setProductId(tool.getId());
        item1.setProductName(tool.getName());
        item1.setQty(2);
        item1.setPricePerUnit(null);

        // Item 2: pricePerUnit == 0.00 -> explicit zero price override
        InvoiceRequestItem item2 = new InvoiceRequestItem();
        item2.setProductId(tool.getId());
        item2.setProductName(tool.getName());
        item2.setQty(1);
        item2.setPricePerUnit(BigDecimal.ZERO);

        // Item 3: pricePerUnit == 1850.00 -> manual override
        InvoiceRequestItem item3 = new InvoiceRequestItem();
        item3.setProductId(tool.getId());
        item3.setProductName(tool.getName());
        item3.setQty(1);
        item3.setPricePerUnit(new BigDecimal("1850.00"));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(customer.getId());
        req.setPricingProfileId(contractorProfile.getId());
        req.setItems(Arrays.asList(item1, item2, item3));
        req.setFirmId(FIRM_A);

        Invoice inv = new Invoice();
        inv.setFirmId(FIRM_A);
        inv.setPricingProfileId(contractorProfile.getId());

        Invoice calculated = calculationEngine.calculate(inv, customer, Collections.singletonList(tool), req, false);
        assertNotNull(calculated);
        assertEquals(3, calculated.getItems().size());

        // Item 1: 1700.00 * 2 = 3400.00
        assertEquals(new BigDecimal("1700.00").stripTrailingZeros(), calculated.getItems().get(0).getPricePerUnit().stripTrailingZeros());
        assertEquals(new BigDecimal("3400.00").stripTrailingZeros(), calculated.getItems().get(0).getAmountWithoutTax().stripTrailingZeros());

        // Item 2: 0.00
        assertEquals(new BigDecimal("0.00").stripTrailingZeros(), calculated.getItems().get(1).getPricePerUnit().stripTrailingZeros());
        assertEquals(new BigDecimal("0.00").stripTrailingZeros(), calculated.getItems().get(1).getAmountWithoutTax().stripTrailingZeros());

        // Item 3: 1850.00
        assertEquals(new BigDecimal("1850.00").stripTrailingZeros(), calculated.getItems().get(2).getPricePerUnit().stripTrailingZeros());
        assertEquals(new BigDecimal("1850.00").stripTrailingZeros(), calculated.getItems().get(2).getAmountWithoutTax().stripTrailingZeros());

        // Negative price rejection:
        InvoiceRequestItem negItem = new InvoiceRequestItem();
        negItem.setProductId(tool.getId());
        negItem.setProductName(tool.getName());
        negItem.setQty(1);
        negItem.setPricePerUnit(new BigDecimal("-50.00"));

        InvoiceRequest badReq = new InvoiceRequest();
        badReq.setCustomerId(customer.getId());
        badReq.setItems(Collections.singletonList(negItem));
        badReq.setFirmId(FIRM_A);

        final Product finalTool = tool;
        final Customer finalCust = customer;

        assertThrows(IllegalArgumentException.class, () -> {
            calculationEngine.calculate(new Invoice(), finalCust, Collections.singletonList(finalTool), badReq, false);
        });
    }

    @Test
    @DisplayName("Invoice Pricing Flow: Historical invoices retain their snapshot prices when category rules change later")
    void testInvoiceHistoricalSnapshotImmutability() {
        // Base = 10000, Retail +5% -> 10500
        PricingProfileDto retailProfile = productPricingService.createProfile(
                PricingProfileDto.builder()
                        .name("Retail Store")
                        .code("RETAIL_STORE")
                        .isDefault(false)
                        .isActive(true)
                        .build(),
                FIRM_A
        );

        CategoryPricingRule rule = CategoryPricingRule.builder()
                .firmId(FIRM_A)
                .pricingProfileId(retailProfile.getId())
                .categoryName("Appliance")
                .adjustmentType("PERCENT")
                .adjustmentValue(new BigDecimal("5.00")) // +5%
                .build();
        categoryPricingRuleRepo.save(rule);

        Product tv = new Product();
        tv.setName("Smart TV 55 inch");
        tv.setCategory("Appliance");
        tv.setPrice(new BigDecimal("10000.00")); // Base
        tv.setStockQuantity(new BigDecimal("100.000"));
        tv.setFirmId(FIRM_A);
        tv = productRepo.save(tv);

        Customer customer = new Customer();
        customer.setName("John Doe");
        customer.setFirmId(FIRM_A);
        customer = customerRepo.save(customer);

        // 1. Create Invoice 1 with null pricePerUnit -> derived to 10500.00
        InvoiceRequestItem item1 = new InvoiceRequestItem();
        item1.setProductId(tv.getId());
        item1.setQty(1);
        item1.setPricePerUnit(null);

        InvoiceRequest req1 = new InvoiceRequest();
        req1.setCustomerId(customer.getId());
        req1.setPricingProfileId(retailProfile.getId());
        req1.setItems(Collections.singletonList(item1));
        req1.setFirmId(FIRM_A);

        Invoice invoice1 = invoiceService.createInvoice(req1);
        assertNotNull(invoice1.getId());
        assertEquals(new BigDecimal("10500.00").stripTrailingZeros(), invoice1.getItems().get(0).getPricePerUnit().stripTrailingZeros());

        // 2. Change the Category Rule to +10%
        rule.setAdjustmentValue(new BigDecimal("10.00")); // +10%
        categoryPricingRuleRepo.save(rule);

        // 3. Reload Old Invoice 1 -> MUST still contain 10500.00
        Invoice reloaded1 = invoiceRepo.findByIdAndFirmId(invoice1.getId(), FIRM_A).orElse(null);
        assertNotNull(reloaded1);
        assertEquals(new BigDecimal("10500.00").stripTrailingZeros(), reloaded1.getItems().get(0).getPricePerUnit().stripTrailingZeros());

        // 4. Create New Invoice 2 after rule change -> MUST resolve to 11000.00
        InvoiceRequestItem item2 = new InvoiceRequestItem();
        item2.setProductId(tv.getId());
        item2.setQty(1);
        item2.setPricePerUnit(null);

        InvoiceRequest req2 = new InvoiceRequest();
        req2.setCustomerId(customer.getId());
        req2.setPricingProfileId(retailProfile.getId());
        req2.setItems(Collections.singletonList(item2));
        req2.setFirmId(FIRM_A);

        Invoice invoice2 = invoiceService.createInvoice(req2);
        assertNotNull(invoice2.getId());
        assertEquals(new BigDecimal("11000.00").stripTrailingZeros(), invoice2.getItems().get(0).getPricePerUnit().stripTrailingZeros());
    }

    @Test
    @DisplayName("Estimate Conversion: Converting an estimate preserves quoted prices even if rules change later")
    void testEstimateConversionPricingPreservation() {
        // Base = 10000, Retail +5% -> 10500 quoted on estimate
        PricingProfileDto retailProfile = productPricingService.createProfile(
                PricingProfileDto.builder()
                        .name("Commercial Tier")
                        .code("COMMERCIAL_T")
                        .isDefault(false)
                        .isActive(true)
                        .build(),
                FIRM_A
        );

        CategoryPricingRule rule = CategoryPricingRule.builder()
                .firmId(FIRM_A)
                .pricingProfileId(retailProfile.getId())
                .categoryName("Industrial")
                .adjustmentType("PERCENT")
                .adjustmentValue(new BigDecimal("5.00")) // +5%
                .build();
        categoryPricingRuleRepo.save(rule);

        Product pump = new Product();
        pump.setName("Water Pump X");
        pump.setCategory("Industrial");
        pump.setPrice(new BigDecimal("10000.00")); // Base
        pump.setStockQuantity(new BigDecimal("100.000"));
        pump.setFirmId(FIRM_A);
        pump = productRepo.save(pump);

        Customer customer = new Customer();
        customer.setName("Farmer Union");
        customer.setFirmId(FIRM_A);
        customer = customerRepo.save(customer);

        // 1. Create Estimate
        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(pump.getId());
        item.setQty(1);
        item.setPricePerUnit(null);

        InvoiceRequest estReq = new InvoiceRequest();
        estReq.setCustomerId(customer.getId());
        estReq.setPricingProfileId(retailProfile.getId());
        estReq.setItems(Collections.singletonList(item));
        estReq.setStatus(com.billing.simple.billsoft.entities.InvoiceStatus.ESTIMATE);
        estReq.setFirmId(FIRM_A);

        Invoice estimate = invoiceService.createInvoice(estReq);
        assertNotNull(estimate.getId());
        assertEquals(new BigDecimal("10500.00").stripTrailingZeros(), estimate.getItems().get(0).getPricePerUnit().stripTrailingZeros());

        // 2. Change rule to +20%
        rule.setAdjustmentValue(new BigDecimal("20.00"));
        categoryPricingRuleRepo.save(rule);

        // 3. Convert Estimate to Invoice
        Invoice convertedInvoice = invoiceService.convertEstimateToInvoice(estimate.getId(), null);
        assertNotNull(convertedInvoice.getId());
        // Must preserve the quoted 10500.00 from estimate, NOT reprice to 12000.00
        assertEquals(new BigDecimal("10500.00").stripTrailingZeros(), convertedInvoice.getItems().get(0).getPricePerUnit().stripTrailingZeros());
    }
}
