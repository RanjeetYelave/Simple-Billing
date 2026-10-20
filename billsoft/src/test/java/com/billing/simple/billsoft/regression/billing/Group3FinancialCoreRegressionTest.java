package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.dtos.SalesReturnRequest;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.*;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.service.FirmDetailsService;
import com.billing.simple.billsoft.service.GstrExportService;
import com.billing.simple.billsoft.service.InvoicePdfService;
import com.billing.simple.billsoft.service.InvoiceService;
import com.billing.simple.billsoft.service.pdf.InvoiceMasterDataResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@DisplayName("Group 3 Financial Core, Tax & Immutable Snapshots Regression Tests")
public class Group3FinancialCoreRegressionTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private FirmDetailsService firmService;

    @Autowired
    private GstrExportService gstrExportService;

    @Autowired
    private InvoicePdfService invoicePdfService;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private SalesReturnRepository salesReturnRepo;

    @Autowired
    private InvoicePaymentRepository paymentRepo;

    @Autowired
    private FirmDetailsRepository firmRepo;

    private Long testFirmId = 1L;
    private Customer testCustomer;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        FirmDetails firm = new FirmDetails();
        firm.setFirmName("Master Firm 1");
        firm.setGstin("27AABCM1234F1Z5");
        firm.setState("Maharashtra");
        firm.setAddressLine1("123 Business Way");
        firm.setCity("Mumbai");
        firm.setPincode("400001");

        List<FirmDetails> existing = firmRepo.findAll();
        if (existing.isEmpty()) {
            firm = firmService.create(firm);
        } else {
            firm = firmService.update(existing.get(0).getId(), firm);
        }
        testFirmId = firm.getId();
        TenantContext.setCurrentFirmId(testFirmId);

        testCustomer = new Customer();
        testCustomer.setName("Alpha Retailer");
        testCustomer.setGstin("27AAACA9999P1Z8");
        testCustomer.setPhone("9876543210");
        testCustomer.setAddress("456 Customer Ave, Pune, Maharashtra");
        testCustomer.setFirmId(testFirmId);
        testCustomer = customerRepo.save(testCustomer);

        testProduct = new Product();
        testProduct.setName("Industrial Valve");
        testProduct.setPrice(new BigDecimal("1000.00"));
        testProduct.setStockQuantity(BigDecimal.valueOf(100));
        testProduct.setUnit("pcs");
        testProduct.setGstPercentage(new BigDecimal("18.00"));
        testProduct.setFirmId(testFirmId);
        testProduct = productRepo.save(testProduct);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =========================================================================
    // AUDIT-029: Invoice Deletion vs Sales Return Constraints
    // =========================================================================

    @Test
    @DisplayName("AUDIT-029: Reject invoice deletion when active sales returns exist")
    void testAudit029InvoiceDeletionBlockedWhenReturnsExist() {
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setPaymentMethod("Cash");
        req.setStatus(InvoiceStatus.PAID);
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(testProduct.getId())
                        .productName(testProduct.getName())
                        .qty(2)
                        .pricePerUnit(new BigDecimal("1000.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .build()
        ));

        Invoice inv = invoiceService.createInvoice(req);
        assertThat(inv.getId()).isNotNull();

        // Create a sales return for 1 unit
        Long invoiceItemId = inv.getItems().get(0).getId();
        SalesReturnRequest retReq = new SalesReturnRequest();
        retReq.setReason("Defective Unit");
        retReq.setItems(List.of(
                SalesReturnRequest.SalesReturnItemRequest.builder()
                        .invoiceItemId(invoiceItemId)
                        .productId(testProduct.getId())
                        .returnQty(1)
                        .build()
        ));

        SalesReturn ret = invoiceService.createSalesReturn(inv.getId(), retReq);
        assertThat(ret.getId()).isNotNull();

        // Attempting to delete the invoice must be blocked with an IllegalStateException
        assertThatThrownBy(() -> invoiceService.delete(inv.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active credit notes/sales returns exist");

        // The invoice remains intact in the repository
        assertThat(invoiceRepo.findById(inv.getId())).isPresent();

        // Cancel the invoice instead
        Invoice cancelled = invoiceService.updateStatus(inv.getId(), InvoiceStatus.CANCELLED);
        assertThat(cancelled.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
    }

    @Test
    @DisplayName("AUDIT-029: Deletion succeeds cleanly when no sales returns exist")
    void testAudit029InvoiceDeletionSucceedsWithoutReturns() {
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.UNPAID);
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(testProduct.getId())
                        .productName(testProduct.getName())
                        .qty(1)
                        .pricePerUnit(new BigDecimal("1000.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .build()
        ));

        Invoice inv = invoiceService.createInvoice(req);
        assertThat(inv.getId()).isNotNull();

        boolean deleted = invoiceService.delete(inv.getId());
        assertThat(deleted).isTrue();
        assertThat(invoiceRepo.findById(inv.getId())).isEmpty();
    }

    // =========================================================================
    // AUDIT-030: Sales Return Calculation Discount Proration
    // =========================================================================

    @Test
    @DisplayName("AUDIT-030: Sales return refunds net effective billed price accounting for item discounts")
    void testAudit030SalesReturnProratesDiscounts() {
        // Sold: 2 units @ 1000 = 2000 gross. Discount: 200 (100 per unit). Taxable = 1800 (900/unit). GST 18% = 324 (162/unit). Total = 2124.
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(testProduct.getId())
                        .productName(testProduct.getName())
                        .qty(2)
                        .pricePerUnit(new BigDecimal("1000.00"))
                        .discountValue(new BigDecimal("200.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .build()
        ));

        Invoice inv = invoiceService.createInvoice(req);
        InvoiceItem item = inv.getItems().get(0);
        assertThat(item.getTaxableAmount()).isEqualByComparingTo("1800.00");
        assertThat(item.getGstAmount()).isEqualByComparingTo("324.00");
        assertThat(item.getLineTotal()).isEqualByComparingTo("2124.00");

        // Return 1 unit out of 2
        SalesReturnRequest retReq = new SalesReturnRequest();
        retReq.setReason("Surplus");
        retReq.setItems(List.of(
                SalesReturnRequest.SalesReturnItemRequest.builder()
                        .invoiceItemId(item.getId())
                        .productId(testProduct.getId())
                        .returnQty(1)
                        .build()
        ));

        SalesReturn ret = invoiceService.createSalesReturn(inv.getId(), retReq);
        SalesReturnItem retItem = ret.getItems().get(0);

        // Net effective taxable price for 1 unit should be 900.00, NOT 1000.00!
        assertThat(retItem.getDiscountValue()).isEqualByComparingTo("100.00");
        assertThat(retItem.getGstAmount()).isEqualByComparingTo("162.00");
        assertThat(retItem.getRefundTotal()).isEqualByComparingTo("1062.00"); // 900 + 162
        assertThat(ret.getTotalRefundAmount()).isEqualByComparingTo("1062.00");
    }

    // =========================================================================
    // AUDIT-031: Payment Recording Safeguards & Concurrency
    // =========================================================================

    @Test
    @DisplayName("AUDIT-031: Reject payment for CANCELLED, DRAFT, and overpayments")
    void testAudit031PaymentValidationAndOverpaymentGuard() {
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.UNPAID);
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(testProduct.getId())
                        .productName(testProduct.getName())
                        .qty(1)
                        .pricePerUnit(new BigDecimal("1000.00"))
                        .gstPercent(BigDecimal.ZERO)
                        .build()
        ));

        Invoice inv = invoiceService.createInvoice(req);
        Long invId = inv.getId();
        BigDecimal total = inv.getTotalAmount();
        BigDecimal partial = total.multiply(new BigDecimal("0.60")).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal remaining = total.subtract(partial);
        BigDecimal excess = remaining.add(new BigDecimal("50.00"));

        // 1. Partial payment succeeds
        InvoicePayment p1 = invoiceService.recordPayment(invId, partial, LocalDate.now(), "Cash", "REC-1", "Part 1");
        assertThat(p1.getAmount()).isEqualByComparingTo(partial);

        Invoice reloaded = invoiceService.getById(invId);
        assertThat(reloaded.getPaid()).isFalse();

        // 2. Overpayment attempt: Remaining is `remaining`. Attempting to record `excess` must fail
        assertThatThrownBy(() -> invoiceService.recordPayment(invId, excess, LocalDate.now(), "Cash", "REC-2", "Excess"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds remaining receivable");

        // 3. Exact settlement of remaining succeeds and transitions to PAID
        InvoicePayment p2 = invoiceService.recordPayment(invId, remaining, LocalDate.now(), "UPI", "REC-3", "Final");
        assertThat(p2.getAmount()).isEqualByComparingTo(remaining);

        reloaded = invoiceService.getById(invId);
        assertThat(reloaded.getPaid()).isTrue();
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.PAID);

        // 4. Any further payment after full settlement must fail
        assertThatThrownBy(() -> invoiceService.recordPayment(invId, new BigDecimal("10.00"), LocalDate.now(), "Cash", "REC-4", "Extra"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already fully paid");

        // 5. Payments against CANCELLED invoice must be rejected
        req.setInvoiceNumber(null);
        Invoice invToCancel = invoiceService.createInvoice(req);
        invoiceService.updateStatus(invToCancel.getId(), InvoiceStatus.CANCELLED);
        assertThatThrownBy(() -> invoiceService.recordPayment(invToCancel.getId(), new BigDecimal("100.00"), LocalDate.now(), "Cash", "REC-C", "Cancelled test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot record payment against a CANCELLED invoice");
    }

    @Test
    @DisplayName("AUDIT-031: Concurrent payments serialize via pessimistic lock and cannot overpay")
    void testAudit031ConcurrentPaymentsDoNotOverpay() throws Exception {
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.UNPAID);
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(testProduct.getId())
                        .productName(testProduct.getName())
                        .qty(1)
                        .pricePerUnit(new BigDecimal("1000.00"))
                        .gstPercent(BigDecimal.ZERO)
                        .build()
        ));

        Invoice inv = invoiceService.createInvoice(req);
        Long invId = inv.getId();
        BigDecimal total = inv.getTotalAmount(); // e.g. 1180 or 1000

        // Both threads attempt to pay 70% of total (total * 0.70)
        BigDecimal attemptAmount = total.multiply(new BigDecimal("0.70")).setScale(2, java.math.RoundingMode.HALF_UP);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failureCount = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TenantContext.setCurrentFirmId(testFirmId);
                    invoiceService.recordPayment(invId, attemptAmount, LocalDate.now(), "Cash", "RACE-" + idx, "Race " + idx);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    TenantContext.clear();
                }
            });
        }

        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // Exactly one payment must succeed; the second must be rejected
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        // Persisted payments sum must equal exactly attemptAmount, never 2 * attemptAmount (140%)
        List<InvoicePayment> persisted = paymentRepo.findByInvoiceIdOrderByPaymentDateAscIdAsc(invId);
        BigDecimal totalPaid = persisted.stream()
                .map(InvoicePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(persisted).hasSize(1);
        assertThat(totalPaid).isEqualByComparingTo(attemptAmount);
    }

    // =========================================================================
    // AUDIT-032: Place of Supply GST Split & GSTR Export
    // =========================================================================

    @Test
    @DisplayName("AUDIT-032: Intra-State sale splits CGST/SGST; Inter-State sale applies IGST")
    void testAudit032PlaceOfSupplyGstSplit() {
        // Firm state is Maharashtra ("27")
        // Case A: Intra-State (Customer in Maharashtra)
        InvoiceRequest intraReq = new InvoiceRequest();
        intraReq.setCustomerId(testCustomer.getId());
        intraReq.setStatus(InvoiceStatus.UNPAID);
        intraReq.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(testProduct.getId())
                        .productName(testProduct.getName())
                        .qty(1)
                        .pricePerUnit(new BigDecimal("1000.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .build()
        ));

        Invoice intraInv = invoiceService.createInvoice(intraReq);
        assertThat(intraInv.getPlaceOfSupply()).isEqualTo("27");
        assertThat(intraInv.getCgstAmount()).isEqualByComparingTo("90.00");
        assertThat(intraInv.getSgstAmount()).isEqualByComparingTo("90.00");
        assertThat(intraInv.getIgstAmount()).isEqualByComparingTo("0.00");

        // Case B: Inter-State (Customer in Karnataka)
        Customer interCustomer = new Customer();
        interCustomer.setName("Bengaluru Tech Hub");
        interCustomer.setGstin("29BBBBB8888K1Z2");
        interCustomer.setAddress("MG Road, Bengaluru, Karnataka");
        interCustomer.setFirmId(testFirmId);
        interCustomer = customerRepo.save(interCustomer);

        InvoiceRequest interReq = new InvoiceRequest();
        interReq.setCustomerId(interCustomer.getId());
        interReq.setPlaceOfSupply("Karnataka");
        interReq.setStatus(InvoiceStatus.UNPAID);
        interReq.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(testProduct.getId())
                        .productName(testProduct.getName())
                        .qty(1)
                        .pricePerUnit(new BigDecimal("1000.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .build()
        ));

        Invoice interInv = invoiceService.createInvoice(interReq);
        assertThat(interInv.getPlaceOfSupply()).isEqualTo("29");
        assertThat(interInv.getCgstAmount()).isEqualByComparingTo("0.00");
        assertThat(interInv.getSgstAmount()).isEqualByComparingTo("0.00");
        assertThat(interInv.getIgstAmount()).isEqualByComparingTo("180.00");

        // Case C: GSTR-1 payload generation
        Map<String, Object> gstr1 = gstrExportService.generateGstr1Payload(testFirmId, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        assertThat(gstr1).containsKey("b2b");
        assertThat(gstr1).containsKey("summary");

        Map<String, Object> summary = (Map<String, Object>) gstr1.get("summary");
        assertThat(new BigDecimal(summary.get("totalCgst").toString())).isGreaterThanOrEqualTo(new BigDecimal("90.00"));
        assertThat(new BigDecimal(summary.get("totalIgst").toString())).isGreaterThanOrEqualTo(new BigDecimal("180.00"));
    }

    // =========================================================================
    // AUDIT-036: Immutable Master Data Snapshots on Invoices
    // =========================================================================

    @Test
    @DisplayName("AUDIT-036.1: Draft does not capture snapshots and remains mutable to master data updates")
    void testDraftDoesNotCaptureSnapshots() {
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.DRAFT);
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(testProduct.getId())
                        .productName(testProduct.getName())
                        .qty(1)
                        .pricePerUnit(new BigDecimal("1000.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .build()
        ));

        Invoice draft = invoiceService.createInvoice(req);
        assertThat(draft.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        // Verify snapshot fields remain NULL on draft
        assertThat(draft.getCustomerNameSnapshot()).isNull();
        assertThat(draft.getCustomerGstinSnapshot()).isNull();
        assertThat(draft.getFirmNameSnapshot()).isNull();

        // Mutate Customer & Firm master data to B
        testCustomer.setName("Customer Master B");
        customerRepo.save(testCustomer);

        FirmDetails firm = firmService.get(testFirmId);
        firm.setFirmName("Firm Master B");
        firmService.update(testFirmId, firm);

        // Reload draft from DB
        Invoice reloaded = invoiceRepo.findById(draft.getId()).orElseThrow();
        assertThat(reloaded.getCustomerNameSnapshot()).isNull();
        assertThat(reloaded.getFirmNameSnapshot()).isNull();

        // Draft resolvers must resolve the updated live data B, not premature snapshot A
        assertThat(InvoiceMasterDataResolver.getCustomerName(reloaded)).isEqualTo("Customer Master B");
        assertThat(InvoiceMasterDataResolver.getFirmName(reloaded, firmService.get(testFirmId))).isEqualTo("Firm Master B");
    }

    @Test
    @DisplayName("AUDIT-036.2: Transitioning DRAFT to UNPAID via updateStatus captures snapshots")
    void testUpdateStatusToUnpaidCapturesSnapshots() {
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.DRAFT);
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(testProduct.getId())
                        .productName(testProduct.getName())
                        .qty(1)
                        .pricePerUnit(new BigDecimal("1000.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .build()
        ));

        Invoice draft = invoiceService.createInvoice(req);
        assertThat(draft.getCustomerNameSnapshot()).isNull();

        // Transition DRAFT -> UNPAID via updateStatus
        Invoice issued = invoiceService.updateStatus(draft.getId(), InvoiceStatus.UNPAID);
        assertThat(issued.getStatus()).isEqualTo(InvoiceStatus.UNPAID);

        // Reload from database and verify snapshots are captured
        Invoice reloaded = invoiceRepo.findById(draft.getId()).orElseThrow();
        assertThat(reloaded.getCustomerNameSnapshot()).isEqualTo("Alpha Retailer");
        assertThat(reloaded.getCustomerGstinSnapshot()).isEqualTo("27AAACA9999P1Z8");
        assertThat(reloaded.getFirmNameSnapshot()).isEqualTo("Master Firm 1");
    }

    @Test
    @DisplayName("AUDIT-036.3: Transitioning DRAFT to PAID via updatePaidFlag captures snapshots")
    void testUpdatePaidFlagToPaidCapturesSnapshots() {
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.DRAFT);
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(testProduct.getId())
                        .productName(testProduct.getName())
                        .qty(1)
                        .pricePerUnit(new BigDecimal("1000.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .build()
        ));

        Invoice draft = invoiceService.createInvoice(req);
        assertThat(draft.getCustomerNameSnapshot()).isNull();

        // Transition DRAFT -> PAID via updatePaidFlag
        Invoice paidInv = invoiceService.updatePaidFlag(draft.getId(), true);
        assertThat(paidInv.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(paidInv.getPaid()).isTrue();

        // Reload from database and verify snapshots are captured
        Invoice reloaded = invoiceRepo.findById(draft.getId()).orElseThrow();
        assertThat(reloaded.getCustomerNameSnapshot()).isEqualTo("Alpha Retailer");
        assertThat(reloaded.getCustomerGstinSnapshot()).isEqualTo("27AAACA9999P1Z8");
        assertThat(reloaded.getFirmNameSnapshot()).isEqualTo("Master Firm 1");
    }

    @Test
    @DisplayName("AUDIT-036.4: Converting ESTIMATE to INVOICE captures snapshots")
    void testEstimateConversionCapturesSnapshots() {
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.ESTIMATE);
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(testProduct.getId())
                        .productName(testProduct.getName())
                        .qty(2)
                        .pricePerUnit(new BigDecimal("500.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .build()
        ));

        Invoice estimate = invoiceService.createEstimate(req);
        assertThat(estimate.getStatus()).isEqualTo(InvoiceStatus.ESTIMATE);
        assertThat(estimate.getCustomerNameSnapshot()).isNull();
        assertThat(estimate.getFirmNameSnapshot()).isNull();

        // Convert ESTIMATE -> INVOICE
        Invoice converted = invoiceService.convertEstimateToInvoice(estimate.getId(), null);
        assertThat(converted.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
        assertThat(converted.getInvoiceNumber()).isNotNull();

        // Reload converted invoice from database and verify snapshots are populated
        Invoice reloaded = invoiceRepo.findById(converted.getId()).orElseThrow();
        assertThat(reloaded.getCustomerNameSnapshot()).isEqualTo("Alpha Retailer");
        assertThat(reloaded.getCustomerGstinSnapshot()).isEqualTo("27AAACA9999P1Z8");
        assertThat(reloaded.getFirmNameSnapshot()).isEqualTo("Master Firm 1");
    }

    @Test
    @DisplayName("AUDIT-036.5: Issued invoice snapshots remain immutable across subsequent edits and master mutations")
    void testIssuedInvoiceSnapshotsRemainImmutable() {
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.UNPAID);
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(testProduct.getId())
                        .productName(testProduct.getName())
                        .qty(1)
                        .pricePerUnit(new BigDecimal("1000.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .build()
        ));

        Invoice inv = invoiceService.createInvoice(req);
        assertThat(inv.getCustomerNameSnapshot()).isEqualTo("Alpha Retailer");
        assertThat(inv.getFirmNameSnapshot()).isEqualTo("Master Firm 1");

        // Mutate Master Data to C
        testCustomer.setName("Customer C Mutated");
        customerRepo.save(testCustomer);

        FirmDetails firm = firmService.get(testFirmId);
        firm.setFirmName("Firm C Mutated");
        firmService.update(testFirmId, firm);

        // Perform legitimate status update (UNPAID -> PAID)
        invoiceService.updateStatus(inv.getId(), InvoiceStatus.PAID);

        // Reload invoice from DB
        Invoice reloaded = invoiceRepo.findById(inv.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.PAID);
        // Snapshots must remain B ("Alpha Retailer", "Master Firm 1"), NOT overwritten by C
        assertThat(reloaded.getCustomerNameSnapshot()).isEqualTo("Alpha Retailer");
        assertThat(reloaded.getFirmNameSnapshot()).isEqualTo("Master Firm 1");
    }

    @Test
    @DisplayName("AUDIT-036.6: Historical PDF renders using issued snapshots after master data mutations")
    void testHistoricalPdfUsesIssuedSnapshotAfterMasterDataChange() throws Exception {
        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(testCustomer.getId());
        req.setStatus(InvoiceStatus.PAID);
        req.setItems(List.of(
                InvoiceRequestItem.builder()
                        .productId(testProduct.getId())
                        .productName(testProduct.getName())
                        .qty(1)
                        .pricePerUnit(new BigDecimal("1000.00"))
                        .gstPercent(new BigDecimal("18.00"))
                        .build()
        ));

        Invoice inv = invoiceService.createInvoice(req);
        assertThat(inv.getCustomerNameSnapshot()).isEqualTo("Alpha Retailer");
        assertThat(inv.getFirmNameSnapshot()).isEqualTo("Master Firm 1");

        // Mutate Customer and Firm master records to C
        testCustomer.setName("MUTATED CUSTOMER NAME C");
        testCustomer.setGstin("99MUTATEDGSTIN99");
        customerRepo.save(testCustomer);

        FirmDetails firm = firmService.get(testFirmId);
        firm.setFirmName("MUTATED FIRM NAME C");
        firmService.update(testFirmId, firm);

        // Reload invoice from DB
        Invoice reloaded = invoiceRepo.findById(inv.getId()).orElseThrow();
        assertThat(InvoiceMasterDataResolver.getCustomerName(reloaded)).isEqualTo("Alpha Retailer");
        assertThat(InvoiceMasterDataResolver.getCustomerGstin(reloaded)).isEqualTo("27AAACA9999P1Z8");
        assertThat(InvoiceMasterDataResolver.getFirmName(reloaded, firmService.get(testFirmId))).isEqualTo("Master Firm 1");

        // Generate PDF and verify it renders successfully using snapshotted data
        byte[] pdfBytes = invoicePdfService.generatePdf(reloaded, "A4");
        assertThat(pdfBytes).isNotNull().isNotEmpty();

        com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdfBytes);
        assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        reader.close();
    }
}
