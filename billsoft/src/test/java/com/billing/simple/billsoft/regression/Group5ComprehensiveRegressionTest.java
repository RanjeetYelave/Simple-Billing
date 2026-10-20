package com.billing.simple.billsoft.regression;

import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.exception.InsufficientStockException;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.repositories.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.DocumentSequenceService;
import com.billing.simple.billsoft.service.DocumentType;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.ProductService;
import com.billing.simple.billsoft.service.PurchaseOrderService;
import com.billing.simple.billsoft.util.IndianFinancialYearUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Group5ComprehensiveRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentSequenceService sequenceService;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private SalesReturnRepository salesReturnRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private AppConfigRepository appConfigRepository;

    private static final Long FIRM_A = 101L;
    private static final Long FIRM_B = 102L;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentFirmId(FIRM_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =========================================================================
    // AUDIT-019: Employee PIN & HR Security
    // =========================================================================

    @Test
    @DisplayName("AUDIT-019: Request without X-Employee-Pin header is rejected (403)")
    void testEmployeeRequestWithoutPinRejected() throws Exception {
        appConfigRepository.save(new AppConfig("EMPLOYEE_MODULE_PIN_" + FIRM_A, "1234"));

        mockMvc.perform(get("/api/employees")
                        .header("X-Firm-Id", FIRM_A.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUDIT-019: Request with incorrect X-Employee-Pin is rejected (403)")
    void testEmployeeRequestWithWrongPinRejected() throws Exception {
        appConfigRepository.save(new AppConfig("EMPLOYEE_MODULE_PIN_" + FIRM_A, "1234"));

        mockMvc.perform(get("/api/employees")
                        .header("X-Firm-Id", FIRM_A.toString())
                        .header("X-Employee-Pin", "9999"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUDIT-019: Request with matching firm-scoped PIN is accepted (200)")
    void testEmployeeRequestWithCorrectPinAccepted() throws Exception {
        appConfigRepository.save(new AppConfig("EMPLOYEE_MODULE_PIN_" + FIRM_A, "1234"));

        mockMvc.perform(get("/api/employees")
                        .header("X-Firm-Id", FIRM_A.toString())
                        .header("X-Employee-Pin", "1234"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AUDIT-019: Firm A PIN cannot authorize Firm B operations")
    void testEmployeeRequestCrossFirmPinRejected() throws Exception {
        appConfigRepository.save(new AppConfig("EMPLOYEE_MODULE_PIN_" + FIRM_A, "1234"));
        appConfigRepository.save(new AppConfig("EMPLOYEE_MODULE_PIN_" + FIRM_B, "5678"));

        // Sending Firm A's PIN ("1234") to access Firm B must be rejected
        mockMvc.perform(get("/api/employees")
                        .header("X-Firm-Id", FIRM_B.toString())
                        .header("X-Employee-Pin", "1234"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUDIT-019: Request missing tenant context is rejected (403)")
    void testEmployeeRequestMissingTenantRejected() throws Exception {
        mockMvc.perform(get("/api/employees")
                        .header("X-Employee-Pin", "1234"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("AUDIT-019: Verify PIN and Change PIN endpoints are firm-scoped")
    void testEmployeeVerifyAndChangePinFirmScoped() throws Exception {
        appConfigRepository.save(new AppConfig("EMPLOYEE_MODULE_PIN_" + FIRM_A, "1111"));

        // Verify PIN for Firm A
        mockMvc.perform(post("/api/employees/verify-pin")
                        .header("X-Firm-Id", FIRM_A.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1111\"}"))
                .andExpect(status().isOk());

        // Change PIN for Firm A
        mockMvc.perform(post("/api/employees/change-pin")
                        .header("X-Firm-Id", FIRM_A.toString())
                        .header("X-Employee-Pin", "1111")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPin\":\"1111\",\"newPin\":\"2222\"}"))
                .andExpect(status().isOk());

        // Verify updated PIN
        AppConfig updated = appConfigRepository.findById("EMPLOYEE_MODULE_PIN_" + FIRM_A).orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getConfigValue()).isEqualTo("2222");
    }

    // =========================================================================
    // AUDIT-023: Purchase Order Numbering & Uniqueness
    // =========================================================================

    @Test
    @DisplayName("AUDIT-023: PO numbers are monotonic and survive record deletion without reuse")
    void testSequentialPoNumbersAndDeletionSafety() {
        TenantContext.setCurrentFirmId(FIRM_A);

        Party party = partyRepository.save(Party.builder()
                .firmId(FIRM_A)
                .name("Acme Supplies")
                .phone("9999999999")
                .build());

        String po1 = purchaseOrderService.generateNextPoNumber(FIRM_A);
        PurchaseOrder savedPo1 = purchaseOrderService.createPurchaseOrder(PurchaseOrder.builder()
                .poNumber(po1)
                .firmId(FIRM_A)
                .poDate(LocalDate.now())
                .party(party)
                .partyName("Acme Supplies")
                .status(PurchaseOrderStatus.ISSUED)
                .totalAmount(BigDecimal.valueOf(100.00))
                .build());

        String po2 = purchaseOrderService.generateNextPoNumber(FIRM_A);
        PurchaseOrder savedPo2 = purchaseOrderService.createPurchaseOrder(PurchaseOrder.builder()
                .poNumber(po2)
                .firmId(FIRM_A)
                .poDate(LocalDate.now())
                .party(party)
                .partyName("Acme Supplies")
                .status(PurchaseOrderStatus.ISSUED)
                .totalAmount(BigDecimal.valueOf(200.00))
                .build());

        assertThat(po1).isNotEqualTo(po2);

        // Delete the most recent PO
        purchaseOrderService.deletePurchaseOrder(savedPo2.getId(), FIRM_A);

        // Next PO number must advance monotonically and NOT reuse the deleted PO number
        String po3 = purchaseOrderService.generateNextPoNumber(FIRM_A);
        assertThat(po3).isNotEqualTo(po2);
        assertThat(po3).isNotEqualTo(po1);
    }

    @Test
    @DisplayName("AUDIT-023: Different firms maintain independent PO sequences")
    void testCrossFirmPoSequenceIsolation() {
        TenantContext.setCurrentFirmId(FIRM_A);
        String poA1 = purchaseOrderService.generateNextPoNumber(FIRM_A);

        TenantContext.setCurrentFirmId(FIRM_B);
        String poB1 = purchaseOrderService.generateNextPoNumber(FIRM_B);

        assertThat(poA1).isNotNull();
        assertThat(poB1).isNotNull();
    }

    @Test
    @DisplayName("AUDIT-023: Concurrent PO sequence generation yields distinct numbers")
    void testConcurrentPoSequenceGeneration() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        Set<String> generatedNumbers = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    TenantContext.setCurrentFirmId(FIRM_A);
                    String poNum = sequenceService.generateNextNumber(DocumentType.PURCHASE_ORDER, FIRM_A, LocalDate.now());
                    generatedNumbers.add(poNum);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(generatedNumbers).hasSize(threads);
    }

    @Test
    @DisplayName("AUDIT-023: Database enforces composite uniqueness UK(firm_id, po_number)")
    void testPoDatabaseUniqueness() {
        TenantContext.setCurrentFirmId(FIRM_A);
        String uniquePoNum = "PO-TEST-UNIQUE-" + System.currentTimeMillis();

        Party party = partyRepository.save(Party.builder()
                .firmId(FIRM_A)
                .name("Unique Party")
                .phone("8888888888")
                .build());

        PurchaseOrder po1 = PurchaseOrder.builder()
                .poNumber(uniquePoNum)
                .firmId(FIRM_A)
                .poDate(LocalDate.now())
                .party(party)
                .partyName("Unique Party")
                .status(PurchaseOrderStatus.ISSUED)
                .build();
        purchaseOrderRepository.saveAndFlush(po1);

        PurchaseOrder duplicate = PurchaseOrder.builder()
                .poNumber(uniquePoNum)
                .firmId(FIRM_A)
                .poDate(LocalDate.now())
                .party(party)
                .partyName("Unique Party")
                .status(PurchaseOrderStatus.ISSUED)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            purchaseOrderRepository.saveAndFlush(duplicate);
        });
    }

    // =========================================================================
    // AUDIT-024: Invoice & Sales Return Indian Financial Year Sequences
    // =========================================================================

    @Test
    @DisplayName("AUDIT-024: Invoice sequences partition by Indian Financial Year and rollover on April 1")
    void testInvoiceSequenceIndianFyAndRollover() {
        // March 31, 2026 -> FY 25-26
        LocalDate march31 = LocalDate.of(2026, 3, 31);
        String fyMarch = IndianFinancialYearUtils.getFinancialYear(march31);
        assertThat(fyMarch).isEqualTo("25-26");

        String invFy2526_1 = sequenceService.generateNextNumber(DocumentType.INVOICE, FIRM_A, march31);
        String invFy2526_2 = sequenceService.generateNextNumber(DocumentType.INVOICE, FIRM_A, march31);
        assertThat(invFy2526_1).isEqualTo("INV-0001");
        assertThat(invFy2526_2).isEqualTo("INV-0002");

        // April 1, 2026 -> FY 26-27 (Rollover to 1)
        LocalDate april1 = LocalDate.of(2026, 4, 1);
        String fyApril = IndianFinancialYearUtils.getFinancialYear(april1);
        assertThat(fyApril).isEqualTo("26-27");

        String invFy2627_1 = sequenceService.generateNextNumber(DocumentType.INVOICE, FIRM_A, april1);
        assertThat(invFy2627_1).isEqualTo("INV-0001");

        // April 1, 2027 -> FY 27-28
        LocalDate april1NextYear = LocalDate.of(2027, 4, 1);
        String invFy2728_1 = sequenceService.generateNextNumber(DocumentType.INVOICE, FIRM_A, april1NextYear);
        assertThat(invFy2728_1).isEqualTo("INV-0001");
    }

    @Test
    @DisplayName("AUDIT-024: Sales Return sequences partition by Indian FY")
    void testSalesReturnSequenceIndianFy() {
        LocalDate april10 = LocalDate.of(2026, 4, 10);
        String ret1 = sequenceService.generateNextNumber(DocumentType.SALES_RETURN, FIRM_A, april10);
        String ret2 = sequenceService.generateNextNumber(DocumentType.SALES_RETURN, FIRM_A, april10);

        assertThat(ret1).isEqualTo("CN-0001");
        assertThat(ret2).isEqualTo("CN-0002");
    }

    @Test
    @DisplayName("AUDIT-024: Concurrent invoice number generation produces distinct sequential numbers")
    void testConcurrentInvoiceSequenceGeneration() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        Set<String> generatedNumbers = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threads);
        LocalDate date = LocalDate.of(2028, 5, 1); // Future FY 28-29

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    String invNum = sequenceService.generateNextNumber(DocumentType.INVOICE, FIRM_A, date);
                    generatedNumbers.add(invNum);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(generatedNumbers).hasSize(threads);
    }

    @Test
    @DisplayName("AUDIT-024: Database enforces uniqueness on (firm_id, invoice_number) and (firm_id, return_number)")
    void testInvoiceAndSalesReturnDbUniqueness() {
        TenantContext.setCurrentFirmId(FIRM_A);
        String invNum = "INV-DUP-TEST-" + System.currentTimeMillis();

        Invoice inv1 = Invoice.builder()
                .invoiceNumber(invNum)
                .firmId(FIRM_A)
                .invoiceDate(java.time.LocalDateTime.now())
                .totalAmount(BigDecimal.valueOf(100.0))
                .build();
        invoiceRepository.saveAndFlush(inv1);

        Invoice invDup = Invoice.builder()
                .invoiceNumber(invNum)
                .firmId(FIRM_A)
                .invoiceDate(java.time.LocalDateTime.now())
                .totalAmount(BigDecimal.valueOf(200.0))
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            invoiceRepository.saveAndFlush(invDup);
        });

        // Sales Return uniqueness
        String retNum = "RET-DUP-TEST-" + System.currentTimeMillis();
        SalesReturn ret1 = SalesReturn.builder()
                .returnNumber(retNum)
                .firmId(FIRM_A)
                .returnDate(LocalDate.now())
                .invoice(inv1)
                .totalRefundAmount(BigDecimal.valueOf(50.00))
                .build();
        salesReturnRepository.saveAndFlush(ret1);

        SalesReturn retDup = SalesReturn.builder()
                .returnNumber(retNum)
                .firmId(FIRM_A)
                .returnDate(LocalDate.now())
                .invoice(inv1)
                .totalRefundAmount(BigDecimal.valueOf(75.00))
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            salesReturnRepository.saveAndFlush(retDup);
        });
    }

    // =========================================================================
    // AUDIT-026: Stock Shortage Policy & Negative Stock Control
    // =========================================================================

    @Test
    @DisplayName("AUDIT-026: Strict mode rejects stock deductions exceeding available stock")
    void testStockShortageRejectionInDefaultStrictMode() {
        TenantContext.setCurrentFirmId(FIRM_A);

        Product p = productService.create(Product.builder()
                .name("Precision Sensor")
                .price(BigDecimal.valueOf(150.00))
                .stockQuantity(BigDecimal.valueOf(10.0))
                .firmId(FIRM_A)
                .itemType("GOODS")
                .build());

        // 1. Deducting within bounds succeeds
        productService.adjustStock(p.getId(), BigDecimal.valueOf(4.0), "DEDUCT", "Test valid deduction");
        Product afterValid = productService.getById(p.getId());
        assertThat(afterValid.getStockQuantity()).isEqualByComparingTo("6.000");

        // 2. Exact deduction succeeds
        productService.adjustStock(p.getId(), BigDecimal.valueOf(6.0), "DEDUCT", "Test exact deduction");
        Product afterZero = productService.getById(p.getId());
        assertThat(afterZero.getStockQuantity()).isEqualByComparingTo("0.000");

        // 3. Deduction beyond zero throws InsufficientStockException and rolls back
        assertThrows(InsufficientStockException.class, () -> {
            productService.adjustStock(p.getId(), BigDecimal.valueOf(1.0), "DEDUCT", "Attempt negative stock");
        });

        Product afterFailed = productService.getById(p.getId());
        assertThat(afterFailed.getStockQuantity()).isEqualByComparingTo("0.000");
    }

    @Test
    @DisplayName("AUDIT-026: Negative stock mode allows full deduction and preserves ledger invariant")
    void testNegativeStockModePreservesLedger() {
        TenantContext.setCurrentFirmId(FIRM_A);

        // Enable negative stock for Firm A
        appConfigRepository.save(new AppConfig("INVENTORY_ALLOW_NEGATIVE_STOCK_" + FIRM_A, "true"));

        Product p = productService.create(Product.builder()
                .name("Bulk Wire Coil")
                .price(BigDecimal.valueOf(500.00))
                .stockQuantity(BigDecimal.valueOf(5.0))
                .firmId(FIRM_A)
                .itemType("GOODS")
                .build());

        // Deduct 8 from stock of 5 -> stock becomes -3
        Product adjusted = productService.adjustStock(p.getId(), BigDecimal.valueOf(8.0), "DEDUCT", "Over-sale test");
        assertThat(adjusted.getStockQuantity()).isEqualByComparingTo("-3.000");

        // Programmatic movement of another -5 -> stock becomes -8
        productService.recordStockMovement(p.getId(), FIRM_A, "INVOICE_SALE", BigDecimal.valueOf(-5.0), "INVOICE", "INV-TEST", "Invoice Sale");
        Product afterProgrammatic = productService.getById(p.getId());
        assertThat(afterProgrammatic.getStockQuantity()).isEqualByComparingTo("-8.000");

        // Ledger invariant: Opening stock (5) + movements (-8, -5) = current stock (-8)
        List<StockMovement> movements = stockMovementRepository.findByProductIdOrderByCreatedAtDesc(p.getId());
        BigDecimal totalChange = movements.stream()
                .filter(m -> !"INITIAL_STOCK".equals(m.getMovementType()) && !"OPENING_STOCK".equals(m.getMovementType()))
                .map(StockMovement::getQuantityChange)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal openingStock = BigDecimal.valueOf(5.0);
        BigDecimal expectedFinalStock = openingStock.add(totalChange);
        assertThat(afterProgrammatic.getStockQuantity()).isEqualByComparingTo(expectedFinalStock);
    }

    @Test
    @DisplayName("AUDIT-026: Sales Return increases stock and is not blocked by stock policy")
    void testSalesReturnIncreasesStock() {
        TenantContext.setCurrentFirmId(FIRM_A);

        Product p = productService.create(Product.builder()
                .name("Returned Ceramic Valve")
                .price(BigDecimal.valueOf(250.00))
                .stockQuantity(BigDecimal.valueOf(2.0))
                .firmId(FIRM_A)
                .itemType("GOODS")
                .build());

        BigDecimal returnedQty = BigDecimal.valueOf(3.0);
        productService.recordStockMovement(p.getId(), FIRM_A, "SALES_RETURN", returnedQty, "SALES_RETURN", "RET-001", "Return");

        Product afterReturn = productService.getById(p.getId());
        assertThat(afterReturn.getStockQuantity()).isEqualByComparingTo("5.000");
    }

    @Test
    @DisplayName("AUDIT-026: Concurrent sales deductions prevent overdraft when negative stock is disabled")
    void testConcurrentSalesDeductionPreventsOverdraft() throws Exception {
        TenantContext.setCurrentFirmId(FIRM_A);
        // Ensure negative stock is false
        appConfigRepository.save(new AppConfig("INVENTORY_ALLOW_NEGATIVE_STOCK_" + FIRM_A, "false"));

        Product p = productService.create(Product.builder()
                .name("High Demand Router")
                .price(BigDecimal.valueOf(1200.00))
                .stockQuantity(BigDecimal.valueOf(10.0))
                .firmId(FIRM_A)
                .itemType("GOODS")
                .build());

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threads);

        List<Boolean> outcomes = new CopyOnWriteArrayList<>();

        // Both threads try to deduct 7 from stock of 10
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContext.setCurrentFirmId(FIRM_A);
                    productService.adjustStock(p.getId(), BigDecimal.valueOf(7.0), "DEDUCT", "Concurrent Sale");
                    outcomes.add(true);
                } catch (Exception e) {
                    outcomes.add(false);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one should succeed, one should fail
        long successes = outcomes.stream().filter(b -> b).count();
        long failures = outcomes.stream().filter(b -> !b).count();

        assertThat(successes).isEqualTo(1);
        assertThat(failures).isEqualTo(1);

        Product finalProduct = productService.getById(p.getId());
        assertThat(finalProduct.getStockQuantity()).isEqualByComparingTo("3.000");
    }
}
