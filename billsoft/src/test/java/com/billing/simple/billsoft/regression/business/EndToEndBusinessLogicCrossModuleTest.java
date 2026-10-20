package com.billing.simple.billsoft.regression.business;

import com.billing.simple.billsoft.assistant.controllers.OmnisearchNluController;
import com.billing.simple.billsoft.dtos.*;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.exception.InsufficientStockException;
import com.billing.simple.billsoft.forecast.dto.RestockRecommendationDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Business Logic & Cross-Module Scenario Verification Suite.
 * Validates Scenarios A through H and core invariant integrity across Billing, Inventory,
 * Products, Customers, Orders, Smart Reorder, Returns, Payments, and Multi-Firm Isolation.
 */
@SpringBootTest
@ActiveProfiles("test")
public class EndToEndBusinessLogicCrossModuleTest {

    @Autowired
    private FirmDetailsRepository firmRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private ProductService productService;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoicePaymentRepository invoicePaymentRepo;

    @Autowired
    private SalesReturnRepository salesReturnRepo;

    @Autowired
    private SalesReturnItemRepository salesReturnItemRepo;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepo;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private PartyRepository partyRepo;

    @Autowired
    private CustomerBalanceService customerBalanceService;

    @Autowired
    private RestockAdvisoryService restockAdvisoryService;

    @Autowired
    private GstrExportService gstrExportService;

    @Autowired
    private OmnisearchNluController omnisearchNluController;

    private Long firm1Id;
    private Long firm2Id;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        FirmDetails f1 = new FirmDetails();
        f1.setFirmName("Rupee Firm A");
        f1.setGstin("27AAAAA0000A1Z5");
        f1.setState("Maharashtra");
        f1 = firmRepo.save(f1);
        firm1Id = f1.getId();

        FirmDetails f2 = new FirmDetails();
        f2.setFirmName("Rupee Firm B");
        f2.setGstin("29BBBBB0000B1Z6");
        f2.setState("Karnataka");
        f2 = firmRepo.save(f2);
        firm2Id = f2.getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =========================================================================
    // SCENARIO A: Normal Sale Lifecycle
    // =========================================================================
    @Test
    @DisplayName("Scenario A: Normal sale lifecycle from opening stock to invoicing, balance, and reorder trigger")
    void testScenarioA_NormalSale() {
        TenantContext.setCurrentFirmId(firm1Id);

        // 1. Create Customer
        Customer customer = new Customer();
        customer.setFirmId(firm1Id);
        customer.setName("Retail Shopper A");
        customer.setPhone("9876543210");
        customer.setGstin("27CUSTA0000A1Z1");
        customer = customerRepo.save(customer);

        // 2. Create Product with known opening stock
        Product product = new Product();
        product.setFirmId(firm1Id);
        product.setName("Premium Basmati Rice 5kg");
        product.setStockQuantity(new BigDecimal("50.000"));
        product.setPrice(new BigDecimal("500.00"));
        product.setGstPercentage(new BigDecimal("5.00"));
        product.setMinStockLevel(new BigDecimal("45.000")); // threshold set to 45
        product.setInInventory(true);
        product.setItemType("PRODUCT");
        product = productRepo.save(product);

        // 3. Create and Save Invoice for 10 units
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firm1Id);
        req.setCustomerId(customer.getId());
        req.setStatus(InvoiceStatus.UNPAID);
        req.setPlaceOfSupply("27");
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .qty(10)
                        .pricePerUnit(new BigDecimal("500.00"))
                        .gstPercent(new BigDecimal("5.00"))
                        .build()
        ));

        Invoice invoice = invoiceService.createInvoice(req);

        // 4. Verify Invoice Totals & Persisted Line Items
        assertThat(invoice.getId()).isNotNull();
        assertThat(invoice.getSubtotalWithoutTax()).isEqualByComparingTo("5000.00");
        assertThat(invoice.getTotalTax()).isEqualByComparingTo("250.00");
        assertThat(invoice.getCgstAmount()).isEqualByComparingTo("125.00");
        assertThat(invoice.getSgstAmount()).isEqualByComparingTo("125.00");
        assertThat(invoice.getIgstAmount()).isEqualByComparingTo("0.00");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("5250.00");

        // 5. Verify Expected Stock Deduction
        Product updatedProduct = productRepo.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStockQuantity()).isEqualByComparingTo("40.000"); // 50 - 10 = 40

        List<StockMovement> movements = productService.getStockMovements(product.getId(), firm1Id);
        assertThat(movements).isNotEmpty();
        StockMovement saleMov = movements.get(0);
        assertThat(saleMov.getMovementType()).isEqualTo("INVOICE_SALE");
        assertThat(saleMov.getQuantityChange()).isEqualByComparingTo("-10.000");
        assertThat(saleMov.getNewStock()).isEqualByComparingTo("40.000");

        // 6. Verify Customer Outstanding Balance
        CustomerBalanceSummary balance = customerBalanceService.getCustomerBalance(firm1Id, customer.getId());
        assertThat(balance.getTotalBilled()).isEqualByComparingTo("5250.00");
        assertThat(balance.getTotalPaid()).isEqualByComparingTo("0.00");
        assertThat(balance.getTotalReturns()).isEqualByComparingTo("0.00");
        assertThat(balance.getClosingBalance()).isEqualByComparingTo("5250.00");

        // 7. Verify Smart Reorder Reflects Stock (40.000) <= Min Stock (45.000)
        final Long prodId = product.getId();
        restockAdvisoryService.refreshRecommendationsForProducts(firm1Id, Set.of(prodId));
        Page<RestockRecommendationDto> recs = restockAdvisoryService.getActiveRecommendations(firm1Id, null, PageRequest.of(0, 10));
        assertThat(recs.getContent()).anyMatch(r -> r.getProductId().equals(prodId)
                && r.getRecommendedOrderQty().compareTo(BigDecimal.ZERO) > 0);
    }

    // =========================================================================
    // SCENARIO B: Sale with Partial and Final Payments
    // =========================================================================
    @Test
    @DisplayName("Scenario B: Invoice payment allocation, progressive settlement, and idempotency")
    void testScenarioB_PartialAndFinalPayment() {
        TenantContext.setCurrentFirmId(firm1Id);

        Customer customer = new Customer();
        customer.setFirmId(firm1Id);
        customer.setName("Payment Test Customer");
        customer = customerRepo.save(customer);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firm1Id);
        req.setCustomerId(customer.getId());
        req.setStatus(InvoiceStatus.UNPAID);
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productName("Consulting Services")
                        .qty(1)
                        .pricePerUnit(new BigDecimal("1000.00"))
                        .gstPercent(BigDecimal.ZERO)
                        .build()
        ));

        Invoice invoice = invoiceService.createInvoice(req);
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1000.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);

        // 1. Partial Payment of Rs 400
        InvoicePayment payment1 = invoiceService.recordPayment(invoice.getId(), new BigDecimal("400.00"), LocalDate.now(), "CASH", "REC-01", "Partial payment 1");
        assertThat(payment1.getId()).isNotNull();

        CustomerBalanceSummary balPartial = customerBalanceService.getCustomerBalance(firm1Id, customer.getId());
        assertThat(balPartial.getTotalBilled()).isEqualByComparingTo("1000.00");
        assertThat(balPartial.getTotalPaid()).isEqualByComparingTo("400.00");
        assertThat(balPartial.getClosingBalance()).isEqualByComparingTo("600.00");

        // 2. Final Payment of Rs 600
        InvoicePayment payment2 = invoiceService.recordPayment(invoice.getId(), new BigDecimal("600.00"), LocalDate.now(), "UPI", "REC-02", "Final settlement");
        assertThat(payment2.getId()).isNotNull();

        Invoice settledInvoice = invoiceRepo.findById(invoice.getId()).orElseThrow();
        assertThat(settledInvoice.getPaid()).isTrue();
        assertThat(settledInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);

        CustomerBalanceSummary balSettled = customerBalanceService.getCustomerBalance(firm1Id, customer.getId());
        assertThat(balSettled.getTotalBilled()).isEqualByComparingTo("1000.00");
        assertThat(balSettled.getTotalPaid()).isEqualByComparingTo("1000.00");
        assertThat(balSettled.getClosingBalance()).isEqualByComparingTo("0.00");

        // 3. Attempt over-payment / redundant payment beyond total
        assertThrows(IllegalArgumentException.class, () -> {
            invoiceService.recordPayment(invoice.getId(), new BigDecimal("100.00"), LocalDate.now(), "CASH", "REC-03", "Overpayment attempt");
        });
    }

    // =========================================================================
    // SCENARIO C: Sale, Edit (Delta Reconciliation), and Cancellation
    // =========================================================================
    @Test
    @DisplayName("Scenario C: Sale, delta stock reconciliation on edit, and stock restoration on cancellation")
    void testScenarioC_SaleEditAndCancellation() {
        TenantContext.setCurrentFirmId(firm1Id);

        Product product = new Product();
        product.setFirmId(firm1Id);
        product.setName("Office Chair Ergonomic");
        product.setStockQuantity(new BigDecimal("20.000"));
        product.setPrice(new BigDecimal("2000.00"));
        product.setGstPercentage(BigDecimal.ZERO);
        product.setInInventory(true);
        product = productRepo.save(product);

        Customer customer = new Customer();
        customer.setFirmId(firm1Id);
        customer.setName("Acme Corp");
        customer = customerRepo.save(customer);

        // 1. Initial Sale: 10 units (Stock: 20 -> 10)
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firm1Id);
        req.setCustomerId(customer.getId());
        req.setStatus(InvoiceStatus.UNPAID);
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .qty(10)
                        .pricePerUnit(new BigDecimal("2000.00"))
                        .gstPercent(BigDecimal.ZERO)
                        .build()
        ));
        Invoice invoice = invoiceService.createInvoice(req);
        assertThat(productRepo.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualByComparingTo("10.000");

        // 2. Edit Invoice: Reduce quantity from 10 to 6 (Delta reconciliation: restore 4 units, Stock: 10 -> 14)
        InvoiceUpdateRequest updateReq = new InvoiceUpdateRequest();
        updateReq.setCustomerId(customer.getId());
        updateReq.setStatus(InvoiceStatus.UNPAID);
        updateReq.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .qty(6)
                        .pricePerUnit(new BigDecimal("2000.00"))
                        .gstPercent(BigDecimal.ZERO)
                        .build()
        ));

        Invoice updatedInvoice = invoiceService.updateFullInvoice(invoice.getId(), updateReq);
        assertThat(updatedInvoice.getTotalAmount()).isEqualByComparingTo("12000.00");
        assertThat(productRepo.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualByComparingTo("14.000");

        CustomerBalanceSummary balAfterEdit = customerBalanceService.getCustomerBalance(firm1Id, customer.getId());
        assertThat(balAfterEdit.getClosingBalance()).isEqualByComparingTo("12000.00");

        // 3. Cancel Invoice: Status -> CANCELLED (Restores remaining 6 units, Stock: 14 -> 20)
        Invoice cancelledInvoice = invoiceService.updateStatus(invoice.getId(), InvoiceStatus.CANCELLED);
        assertThat(cancelledInvoice.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(productRepo.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualByComparingTo("20.000");

        // 4. Verify Customer Balance drops to 0 after cancellation
        CustomerBalanceSummary balCancelled = customerBalanceService.getCustomerBalance(firm1Id, customer.getId());
        assertThat(balCancelled.getTotalBilled()).isEqualByComparingTo("0.00");
        assertThat(balCancelled.getClosingBalance()).isEqualByComparingTo("0.00");
    }

    // =========================================================================
    // SCENARIO D: Partial Return, Over-Return Rejection, and Credit Note
    // =========================================================================
    @Test
    @DisplayName("Scenario D: Sales return, stock restoration, credit note debt reduction, and over-return prevention")
    void testScenarioD_PartialReturnAndCreditNote() {
        TenantContext.setCurrentFirmId(firm1Id);

        Product product = new Product();
        product.setFirmId(firm1Id);
        product.setName("Wireless Keyboard");
        product.setStockQuantity(new BigDecimal("30.000"));
        product.setPrice(new BigDecimal("800.00"));
        product.setGstPercentage(new BigDecimal("18.00"));
        product.setInInventory(true);
        product = productRepo.save(product);

        Customer customer = new Customer();
        customer.setFirmId(firm1Id);
        customer.setName("Return Test Customer");
        customer = customerRepo.save(customer);

        // 1. Sell 10 units (Stock: 30 -> 20)
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firm1Id);
        req.setCustomerId(customer.getId());
        req.setStatus(InvoiceStatus.UNPAID);
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .qty(10)
                        .pricePerUnit(new BigDecimal("800.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .build()
        ));
        Invoice invoice = invoiceService.createInvoice(req);
        assertThat(productRepo.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualByComparingTo("20.000");

        InvoiceItem billedItem = invoice.getItems().get(0);

        // 2. Return 4 units
        SalesReturnRequest retReq = new SalesReturnRequest();
        retReq.setFirmId(firm1Id);
        retReq.setReason("Defective batch");
        retReq.setItems(List.of(
                SalesReturnRequest.SalesReturnItemRequest.builder()
                        .invoiceItemId(billedItem.getId())
                        .productId(product.getId())
                        .productName(product.getName())
                        .returnQty(4)
                        .unitPrice(new BigDecimal("800.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .build()
        ));

        SalesReturn salesReturn = invoiceService.createSalesReturn(invoice.getId(), retReq);
        assertThat(salesReturn.getId()).isNotNull();

        // Stock restored: 20 + 4 = 24
        assertThat(productRepo.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualByComparingTo("24.000");

        // Credit note value: 4 * 800 + 18% = 3200 + 576 = 3776.00
        assertThat(salesReturn.getTotalRefundAmount()).isEqualByComparingTo("3776.00");

        // Customer balance reduced: 9440.00 (billed) - 3776.00 (returns) = 5664.00
        CustomerBalanceSummary balReturn = customerBalanceService.getCustomerBalance(firm1Id, customer.getId());
        assertThat(balReturn.getTotalReturns()).isEqualByComparingTo("3776.00");
        assertThat(balReturn.getClosingBalance()).isEqualByComparingTo("5664.00");

        // 3. Attempt over-return: trying to return 7 units when only 6 remain returnable
        SalesReturnRequest overRetReq = new SalesReturnRequest();
        overRetReq.setFirmId(firm1Id);
        overRetReq.setItems(List.of(
                SalesReturnRequest.SalesReturnItemRequest.builder()
                        .invoiceItemId(billedItem.getId())
                        .productId(product.getId())
                        .returnQty(7)
                        .build()
        ));

        assertThrows(IllegalArgumentException.class, () -> {
            invoiceService.createSalesReturn(invoice.getId(), overRetReq);
        });

        // 4. Ensure invoice deletion is safely rejected when active credit notes exist
        assertThrows(IllegalStateException.class, () -> {
            invoiceService.delete(invoice.getId());
        });
    }

    // =========================================================================
    // SCENARIO E: Restock Cycle (Smart Reorder -> PO -> Stock Receipt)
    // =========================================================================
    @Test
    @DisplayName("Scenario E: Restock cycle from threshold trigger to Purchase Order and goods receipt")
    void testScenarioE_RestockCycle() {
        TenantContext.setCurrentFirmId(firm1Id);

        // 1. Product with stock below threshold
        Product product = new Product();
        product.setFirmId(firm1Id);
        product.setName("LED Monitor 24-inch");
        product.setStockQuantity(new BigDecimal("3.000"));
        product.setMinStockLevel(new BigDecimal("15.000")); // shortfall = 12
        product.setPrice(new BigDecimal("8500.00"));
        product.setInInventory(true);
        product = productRepo.save(product);

        // 2. Generate and verify recommendation
        final Long prodId = product.getId();
        restockAdvisoryService.refreshRecommendationsForProducts(firm1Id, Set.of(prodId));
        Page<RestockRecommendationDto> recs = restockAdvisoryService.getActiveRecommendations(firm1Id, null, PageRequest.of(0, 10));
        RestockRecommendationDto rec = recs.getContent().stream()
                .filter(r -> r.getProductId().equals(prodId))
                .findFirst()
                .orElseThrow();
        assertThat(rec.getRecommendedOrderQty()).isGreaterThanOrEqualTo(new BigDecimal("12.000"));

        // 3. Create Vendor and Purchase Order for 20 units
        Party party = new Party();
        party.setFirmId(firm1Id);
        party.setName("Apex Displays Vendor");
        party.setPhone("9988776655");
        Party savedVendor = partyRepo.save(party);

        PurchaseOrder po = new PurchaseOrder();
        po.setFirmId(firm1Id);
        po.setParty(savedVendor);
        po.setStatus(PurchaseOrderStatus.ISSUED);
        po.setPoDate(LocalDate.now());
        po.setItems(List.of(
                PurchaseOrderItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .quantity(new BigDecimal("20.000"))
                        .unitPrice(new BigDecimal("6000.00"))
                        .gstPercent(BigDecimal.ZERO)
                        .build()
        ));

        PurchaseOrder createdPo = purchaseOrderService.createPurchaseOrder(po);
        assertThat(createdPo.getId()).isNotNull();

        // 4. Mark PO as RECEIVED -> goods receipt
        PurchaseOrder receivedPo = purchaseOrderService.updateStatus(createdPo.getId(), firm1Id, PurchaseOrderStatus.RECEIVED);
        assertThat(receivedPo.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);

        // 5. Verify inventory physically increased: 3 + 20 = 23
        Product replenishedProduct = productRepo.findById(product.getId()).orElseThrow();
        assertThat(replenishedProduct.getStockQuantity()).isEqualByComparingTo("23.000");

        // 6. Verify Stock Movement entry
        List<StockMovement> movements = productService.getStockMovements(product.getId(), firm1Id);
        assertThat(movements).anyMatch(m -> "PURCHASE_RECEIPT".equals(m.getMovementType())
                && m.getQuantityChange().compareTo(new BigDecimal("20.000")) == 0
                && m.getNewStock().compareTo(new BigDecimal("23.000")) == 0);
    }

    // =========================================================================
    // SCENARIO F: Quotation / Estimate to Invoice Conversion
    // =========================================================================
    @Test
    @DisplayName("Scenario F: Quotation/estimate does not deduct stock; conversion to invoice deducts stock exactly once")
    void testScenarioF_EstimateToInvoiceConversion() {
        TenantContext.setCurrentFirmId(firm1Id);

        Product product = new Product();
        product.setFirmId(firm1Id);
        product.setName("Solar Inverter 5kVA");
        product.setStockQuantity(new BigDecimal("10.000"));
        product.setPrice(new BigDecimal("45000.00"));
        product.setGstPercentage(new BigDecimal("18.00"));
        product.setInInventory(true);
        product = productRepo.save(product);

        Customer customer = new Customer();
        customer.setFirmId(firm1Id);
        customer.setName("Solar Project Client");
        customer = customerRepo.save(customer);

        // 1. Create Quotation/Estimate
        InvoiceRequest estReq = new InvoiceRequest();
        estReq.setFirmId(firm1Id);
        estReq.setCustomerId(customer.getId());
        estReq.setStatus(InvoiceStatus.ESTIMATE);
        estReq.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .qty(2)
                        .pricePerUnit(new BigDecimal("45000.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .build()
        ));

        Invoice estimate = invoiceService.createEstimate(estReq);
        assertThat(estimate.getStatus()).isEqualTo(InvoiceStatus.ESTIMATE);
        assertThat(estimate.getEstimateNumber()).isNotNull();
        assertThat(estimate.getInvoiceNumber()).isNull();

        // Verify stock is UNCHANGED for estimate
        assertThat(productRepo.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualByComparingTo("10.000");

        // 2. Convert Estimate to Invoice
        Invoice invoice = invoiceService.convertEstimateToInvoice(estimate.getId(), null);
        assertThat(invoice.getId()).isNotNull();
        assertThat(invoice.getInvoiceNumber()).isNotNull();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);

        // Verify stock deducted exactly once (10 - 2 = 8)
        assertThat(productRepo.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualByComparingTo("8.000");

        // 3. Verify Estimate points to converted invoice
        Invoice reloadedEstimate = invoiceRepo.findById(estimate.getId()).orElseThrow();
        assertThat(reloadedEstimate.getConvertedInvoiceId()).isEqualTo(invoice.getId());

        // 4. Duplicate conversion attempt must be rejected
        assertThrows(RuntimeException.class, () -> {
            invoiceService.convertEstimateToInvoice(estimate.getId(), null);
        });
    }

    // =========================================================================
    // SCENARIO G: Concurrency & Overselling Prevention
    // =========================================================================
    @Test
    @DisplayName("Scenario G: Pessimistic write locking prevents concurrent overselling below zero stock")
    void testScenarioG_ConcurrencyAndOversellingPrevention() throws InterruptedException {
        TenantContext.setCurrentFirmId(firm1Id);

        // Product with exactly 5 units in stock, negative stock disallowed
        Product product = new Product();
        product.setFirmId(firm1Id);
        product.setName("Limited Edition Watch");
        product.setStockQuantity(new BigDecimal("5.000"));
        product.setPrice(new BigDecimal("15000.00"));
        product.setGstPercentage(BigDecimal.ZERO);
        product.setInInventory(true);
        product = productRepo.save(product);

        final Long prodId = product.getId();
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContext.setCurrentFirmId(firm1Id);
                    productService.recordStockMovement(
                            prodId,
                            firm1Id,
                            "INVOICE_SALE",
                            new BigDecimal("-1.000"),
                            "INVOICE",
                            "CONCURRENCY-TEST",
                            "Concurrent purchase"
                    );
                    successCount.incrementAndGet();
                } catch (InsufficientStockException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    TenantContext.clear();
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Exactly 5 sales must succeed, exactly 5 must be rejected
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failureCount.get()).isEqualTo(5);

        // Final physical stock must be exactly 0, never negative
        Product finalProduct = productRepo.findById(prodId).orElseThrow();
        assertThat(finalProduct.getStockQuantity()).isEqualByComparingTo("0.000");
    }

    // =========================================================================
    // SCENARIO H: Multi-Firm Data Partitioning
    // =========================================================================
    @Test
    @DisplayName("Scenario H: Multi-firm data isolation across catalog, stock, ledger, and customer accounts")
    void testScenarioH_MultiFirmDataIsolation() {
        // Firm 1 Setup
        TenantContext.setCurrentFirmId(firm1Id);
        Customer cust1 = new Customer();
        cust1.setFirmId(firm1Id);
        cust1.setName("Acme Shared Name");
        cust1 = customerRepo.save(cust1);

        Product prod1 = new Product();
        prod1.setFirmId(firm1Id);
        prod1.setName("Universal Adapter");
        prod1.setStockQuantity(new BigDecimal("100.000"));
        prod1.setPrice(new BigDecimal("200.00"));
        prod1.setGstPercentage(BigDecimal.ZERO);
        prod1 = productRepo.save(prod1);

        InvoiceRequest req1 = new InvoiceRequest();
        req1.setFirmId(firm1Id);
        req1.setCustomerId(cust1.getId());
        req1.setStatus(InvoiceStatus.UNPAID);
        req1.setItems(List.of(
                InvoiceRequestItem.builder().productId(prod1.getId()).productName(prod1.getName()).qty(20).pricePerUnit(new BigDecimal("200.00")).gstPercent(BigDecimal.ZERO).build()
        ));
        Invoice inv1 = invoiceService.createInvoice(req1);

        // Firm 2 Setup
        TenantContext.setCurrentFirmId(firm2Id);
        Customer cust2 = new Customer();
        cust2.setFirmId(firm2Id);
        cust2.setName("Acme Shared Name");
        cust2 = customerRepo.save(cust2);

        Product prod2 = new Product();
        prod2.setFirmId(firm2Id);
        prod2.setName("Universal Adapter");
        prod2.setStockQuantity(new BigDecimal("50.000"));
        prod2.setPrice(new BigDecimal("300.00"));
        prod2.setGstPercentage(BigDecimal.ZERO);
        prod2 = productRepo.save(prod2);

        InvoiceRequest req2 = new InvoiceRequest();
        req2.setFirmId(firm2Id);
        req2.setCustomerId(cust2.getId());
        req2.setStatus(InvoiceStatus.UNPAID);
        req2.setItems(List.of(
                InvoiceRequestItem.builder().productId(prod2.getId()).productName(prod2.getName()).qty(10).pricePerUnit(new BigDecimal("300.00")).gstPercent(BigDecimal.ZERO).build()
        ));
        Invoice inv2 = invoiceService.createInvoice(req2);

        // Verify Firm 1 Isolations
        TenantContext.setCurrentFirmId(firm1Id);
        assertThat(productRepo.findById(prod1.getId()).orElseThrow().getStockQuantity()).isEqualByComparingTo("80.000");
        CustomerBalanceSummary bal1 = customerBalanceService.getCustomerBalance(firm1Id, cust1.getId());
        assertThat(bal1.getTotalBilled()).isEqualByComparingTo("4000.00");

        // Verify Firm 2 Isolations
        TenantContext.setCurrentFirmId(firm2Id);
        assertThat(productRepo.findById(prod2.getId()).orElseThrow().getStockQuantity()).isEqualByComparingTo("40.000");
        CustomerBalanceSummary bal2 = customerBalanceService.getCustomerBalance(firm2Id, cust2.getId());
        assertThat(bal2.getTotalBilled()).isEqualByComparingTo("3000.00");

        // Cross-Firm Leakage Prevention: Firm 2 cannot query Firm 1 Customer Balance
        final Long cust1Id = cust1.getId();
        assertThrows(TenantSecurityException.class, () -> {
            customerBalanceService.getCustomerBalance(firm2Id, cust1Id);
        });

        // Cross-Firm Invoice Fetch Prevention via Service: Querying with firm2 context returns null
        Invoice crossInv = invoiceService.getById(inv1.getId());
        assertThat(crossInv).isNull();
    }

    // =========================================================================
    // REGRESSION VERIFICATIONS: AUDIT-044, AUDIT-043
    // =========================================================================
    @Test
    @DisplayName("AUDIT-044 Verification: GSTR-1 export taxable value accounts for invoice & item discounts")
    void testAudit044_Gstr1DiscountedTaxableBasis() {
        TenantContext.setCurrentFirmId(firm1Id);

        Customer customer = new Customer();
        customer.setFirmId(firm1Id);
        customer.setName("GSTR B2B Recipient");
        customer.setGstin("27ABCDE1234F1Z5");
        customer = customerRepo.save(customer);

        // Invoice: Item 1000, 10% discount (100) -> Taxable: 900, 18% GST: 162
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(firm1Id);
        req.setCustomerId(customer.getId());
        req.setStatus(InvoiceStatus.UNPAID);
        req.setPlaceOfSupply("27");
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productName("Taxable Good")
                        .qty(1)
                        .pricePerUnit(new BigDecimal("1000.00"))
                        .discountValue(new BigDecimal("100.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .build()
        ));

        Invoice inv = invoiceService.createInvoice(req);
        assertThat(inv.getTotalTax()).isEqualByComparingTo("162.00");
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("1062.00");

        // Generate GSTR-1 payload
        final String invNum = inv.getInvoiceNumber();
        Map<String, Object> payload = gstrExportService.generateGstr1Payload(firm1Id, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        List<Map<String, Object>> b2bList = (List<Map<String, Object>>) payload.get("b2b");
        Map<String, Object> exportedInv = b2bList.stream()
                .filter(b -> b.get("inum").equals(invNum))
                .findFirst()
                .orElseThrow();

        // Statutory assertion: Taxable value must be 900.00, NOT the pre-discount 1000.00
        assertThat(new BigDecimal(exportedInv.get("txval").toString())).isEqualByComparingTo("900.00");
        assertThat(new BigDecimal(exportedInv.get("camt").toString())).isEqualByComparingTo("81.00");
        assertThat(new BigDecimal(exportedInv.get("samt").toString())).isEqualByComparingTo("81.00");
    }

    @Test
    @DisplayName("AUDIT-043 Verification: QR code matrix dimensions are bounded to safe limits")
    void testAudit043_QrMatrixSizeClamped() {
        // Enormous size parameter must not throw OutOfMemoryError and must return valid bounded PNG
        ResponseEntity<byte[]> response = omnisearchNluController.getQrImage("upi://pay?pa=rupee@upi", 50000);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().length).isGreaterThan(0);
    }
}
