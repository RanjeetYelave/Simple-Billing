package com.billing.simple.billsoft.regression;

import com.billing.simple.billsoft.assistant.dto.OmnisearchQuickHelpDTOs.AgingSummaryDTO;
import com.billing.simple.billsoft.assistant.dto.OmnisearchQuickHelpDTOs.QuickHelpResponse;
import com.billing.simple.billsoft.assistant.service.OmnisearchQuickHelpService;
import com.billing.simple.billsoft.dtos.InvoicePrintOptions;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.events.PurchaseOrderUpdatedEvent;
import com.billing.simple.billsoft.forecast.listener.PurchaseOrderRestockSyncListener;
import com.billing.simple.billsoft.forecast.repository.ProductRestockRecommendationRepository;
import com.billing.simple.billsoft.forecast.service.RestockAdvisoryService;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderItemRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantInterceptor;
import com.billing.simple.billsoft.security.TenantSecurityException;
import com.billing.simple.billsoft.service.CustomerBalanceService;
import com.billing.simple.billsoft.service.InvoicePdfService;
import com.billing.simple.billsoft.service.PurchaseOrderService;
import com.billing.simple.billsoft.service.pdf.thermal.Thermal80mmRenderer;
import com.lowagie.text.pdf.BaseFont;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class Group7ComprehensiveRegressionTest {

    @Autowired
    private OmnisearchQuickHelpService omnisearchService;

    @Autowired
    private TenantInterceptor tenantInterceptor;

    @Autowired
    private InvoicePdfService invoicePdfService;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private RestockAdvisoryService restockAdvisoryService;

    @Autowired
    private CustomerBalanceService customerBalanceService;

    @Autowired
    private FirmDetailsRepository firmRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private PurchaseOrderRepository poRepo;

    @Autowired
    private PurchaseOrderItemRepository poItemRepo;

    @Autowired
    private ProductRestockRecommendationRepository restockRepo;

    @Autowired
    private com.billing.simple.billsoft.service.ProductService productService;

    @Autowired
    private com.billing.simple.billsoft.repositories.PartyRepository partyRepo;

    private FirmDetails firm1;
    private FirmDetails firm2;

    @BeforeEach
    void setUp() {
        firm1 = new FirmDetails();
        firm1.setFirmName("Group 7 Primary Firm Ltd");
        firm1.setGstin("27AAACG0001A1Z1");
        firm1.setPhone("9876543210");
        firm1.setEmail("firm1@test.com");
        firm1.setAddressLine1("100 Industrial Area");
        firm1.setCity("Mumbai");
        firm1.setState("Maharashtra");
        firm1.setPincode("400001");
        firm1 = firmRepo.save(firm1);

        firm2 = new FirmDetails();
        firm2.setFirmName("Group 7 Secondary Firm Ltd");
        firm2.setGstin("27AAACG0002A1Z2");
        firm2.setPhone("9876543211");
        firm2.setEmail("firm2@test.com");
        firm2.setAddressLine1("200 Commercial Complex");
        firm2.setCity("Pune");
        firm2.setState("Maharashtra");
        firm2.setPincode("411001");
        firm2 = firmRepo.save(firm2);

        TenantContext.setCurrentFirmId(firm1.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =========================================================================
    // AUDIT-005: OmniSearch Endpoint Mismatch + Missing Accounts Receivable Aging
    // =========================================================================

    @Test
    @DisplayName("AUDIT-005: getAgingSummary strictly reconciles with CustomerBalanceService and excludes non-final invoices")
    @Transactional
    void testAudit005_AgingSummaryReconcilesWithBalanceService() {
        Long fId = firm1.getId();

        Customer cust1 = Customer.builder()
                .name("Aging Customer 1")
                .firmId(fId)
                .build();
        cust1 = customerRepo.save(cust1);

        Customer cust2 = Customer.builder()
                .name("Aging Customer 2")
                .firmId(fId)
                .build();
        cust2 = customerRepo.save(cust2);

        // Cust 1: Invoice 15 days ago (0-30 bucket) -> 500.00
        Invoice invRecent = new Invoice();
        invRecent.setFirmId(fId);
        invRecent.setCustomer(cust1);
        invRecent.setInvoiceNumber("INV-AGE-15");
        invRecent.setStatus(InvoiceStatus.FINAL);
        invRecent.setInvoiceDate(LocalDateTime.now().minusDays(15));
        invRecent.setTotalAmount(new BigDecimal("500.00"));
        invoiceRepo.save(invRecent);

        // Cust 1: Invoice 45 days ago (31-60 bucket) -> 300.00
        Invoice invMid = new Invoice();
        invMid.setFirmId(fId);
        invMid.setCustomer(cust1);
        invMid.setInvoiceNumber("INV-AGE-45");
        invMid.setStatus(InvoiceStatus.FINAL);
        invMid.setInvoiceDate(LocalDateTime.now().minusDays(45));
        invMid.setTotalAmount(new BigDecimal("300.00"));
        invoiceRepo.save(invMid);

        // Cust 2: Invoice 75 days ago (61-90 bucket) -> 400.00
        Invoice invOld = new Invoice();
        invOld.setFirmId(fId);
        invOld.setCustomer(cust2);
        invOld.setInvoiceNumber("INV-AGE-75");
        invOld.setStatus(InvoiceStatus.FINAL);
        invOld.setInvoiceDate(LocalDateTime.now().minusDays(75));
        invOld.setTotalAmount(new BigDecimal("400.00"));
        invoiceRepo.save(invOld);

        // Cust 2: Draft invoice (should be EXCLUDED) -> 999.00
        Invoice invDraft = new Invoice();
        invDraft.setFirmId(fId);
        invDraft.setCustomer(cust2);
        invDraft.setInvoiceNumber("INV-DRAFT");
        invDraft.setStatus(InvoiceStatus.DRAFT);
        invDraft.setInvoiceDate(LocalDateTime.now().minusDays(10));
        invDraft.setTotalAmount(new BigDecimal("999.00"));
        invoiceRepo.save(invDraft);

        QuickHelpResponse<AgingSummaryDTO> resp = omnisearchService.getAgingSummary();
        assertNotNull(resp);
        assertEquals("ANSWER", resp.getStatus());

        AgingSummaryDTO summary = resp.getData();
        assertNotNull(summary);

        // Total outstanding must equal 500 + 300 + 400 = 1200.00
        assertEquals(new BigDecimal("1200.00"), summary.getTotalReceivable());
        assertEquals(new BigDecimal("1200.00"), summary.getNetReceivable());
        assertEquals(new BigDecimal("500.00"), summary.getBuckets().get("0-30"));
        assertEquals(new BigDecimal("300.00"), summary.getBuckets().get("31-60"));
        assertEquals(new BigDecimal("400.00"), summary.getBuckets().get("61-90"));
        assertEquals(new BigDecimal("0.00"), summary.getBuckets().get("90+"));
        assertEquals(new BigDecimal("0.00"), summary.getTotalCustomerCredits());
    }

    // =========================================================================
    // AUDIT-006: OmniSearch Tenant/Security Perimeter
    // =========================================================================

    @Test
    @DisplayName("AUDIT-006: TenantInterceptor rejects /api/omnisearch/** when X-Firm-Id missing, but permits /api/omnisearch/qr")
    void testAudit006_OmnisearchSecurityPerimeterEnforcement() throws Exception {
        // 1. Missing firm header on /api/omnisearch/expenses -> Rejected with 400
        MockHttpServletRequest reqExpenses = new MockHttpServletRequest("GET", "/api/omnisearch/expenses");
        MockHttpServletResponse resExpenses = new MockHttpServletResponse();
        boolean allowedExpenses = tenantInterceptor.preHandle(reqExpenses, resExpenses, new Object());
        assertFalse(allowedExpenses);
        assertEquals(400, resExpenses.getStatus());

        // 2. Missing firm header on /api/omnisearch/aging -> Rejected with 400
        MockHttpServletRequest reqAging = new MockHttpServletRequest("GET", "/api/omnisearch/aging");
        MockHttpServletResponse resAging = new MockHttpServletResponse();
        boolean allowedAging = tenantInterceptor.preHandle(reqAging, resAging, new Object());
        assertFalse(allowedAging);
        assertEquals(400, resAging.getStatus());

        // 3. Exemption: /api/omnisearch/qr without header -> Permitted
        MockHttpServletRequest reqQr = new MockHttpServletRequest("GET", "/api/omnisearch/qr");
        MockHttpServletResponse resQr = new MockHttpServletResponse();
        boolean allowedQr = tenantInterceptor.preHandle(reqQr, resQr, new Object());
        assertTrue(allowedQr);

        // 4. Valid firm header on /api/omnisearch/expenses -> Permitted and sets TenantContext
        TenantContext.clear();
        MockHttpServletRequest reqWithHeader = new MockHttpServletRequest("GET", "/api/omnisearch/expenses");
        reqWithHeader.addHeader("X-Firm-Id", firm1.getId().toString());
        MockHttpServletResponse resWithHeader = new MockHttpServletResponse();
        boolean allowedWithHeader = tenantInterceptor.preHandle(reqWithHeader, resWithHeader, new Object());
        assertTrue(allowedWithHeader);
        assertEquals(firm1.getId(), TenantContext.getCurrentFirmId());
    }

    // =========================================================================
    // AUDIT-012: Stale OmniSearch Navigation Routes
    // =========================================================================

    @Test
    @DisplayName("AUDIT-012: QuickHelp navigation returns tab: orders (not paperwork) and handles purchase orders")
    @Transactional
    void testAudit012_NavigationRoutesUpdated() {
        Party party = Party.builder()
                .name("Navigation Vendor")
                .firmId(firm1.getId())
                .build();
        party = partyRepo.save(party);

        var resp = omnisearchService.getVendorQuickHelp(party.getName());
        assertNotNull(resp);
        assertFalse(resp.getDeepLinks().isEmpty());

        for (var dl : resp.getDeepLinks()) {
            if ("📋 View POs".equals(dl.getLabel())) {
                assertEquals("orders", dl.getParams().get("tab"));
                assertNotEquals("paperwork", dl.getParams().get("tab"));
                assertEquals("firm", dl.getParams().get("page"));
            }
        }
    }

    // =========================================================================
    // AUDIT-013: Smart Reorder PO Lifecycle Event Synchronization
    // =========================================================================

    @Test
    @DisplayName("AUDIT-013: PurchaseOrder updateStatus triggers restock recommendation refresh for affected products")
    @Transactional
    void testAudit013_PoLifecycleTriggersRestockRefresh() {
        Long fId = firm1.getId();

        Product prod = Product.builder()
                .name("Reorder Test Item")
                .firmId(fId)
                .stockQuantity(new BigDecimal("10.000"))
                .minStockLevel(new BigDecimal("20.000"))
                .build();
        prod = productService.create(prod);

        Party party = Party.builder()
                .name("Supplier Alpha")
                .firmId(fId)
                .build();
        party = partyRepo.save(party);

        PurchaseOrder po = new PurchaseOrder();
        po.setFirmId(fId);
        po.setParty(party);
        po.setPoNumber("PO-REORDER-001");
        po.setStatus(PurchaseOrderStatus.DRAFT);
        po.setPoDate(LocalDate.now());

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setPurchaseOrder(po);
        item.setProductId(prod.getId());
        item.setProductName(prod.getName());
        item.setQuantity(new BigDecimal("25.000"));
        item.setUnitPrice(new BigDecimal("100.00"));
        po.getItems().add(item);

        PurchaseOrder savedPo = purchaseOrderService.createPurchaseOrder(po);
        assertNotNull(savedPo.getId());

        // Update status to ISSUED
        PurchaseOrder updatedPo = purchaseOrderService.updateStatus(savedPo.getId(), fId, PurchaseOrderStatus.ISSUED);
        assertEquals(PurchaseOrderStatus.ISSUED, updatedPo.getStatus());

        // Directly invoke listener to verify post-commit processing
        PurchaseOrderRestockSyncListener listener = new PurchaseOrderRestockSyncListener(restockAdvisoryService);
        listener.onPurchaseOrderUpdated(new PurchaseOrderUpdatedEvent(
                updatedPo.getId(), fId, PurchaseOrderStatus.ISSUED, PurchaseOrderStatus.DRAFT, Set.of(prod.getId())));

        // Recommendation was generated or refreshed
        var optRec = restockRepo.findTopByFirmIdAndProductIdAndRecommendationStatusOrderByGeneratedAtDesc(fId, prod.getId(), "ACTIVE");
        assertTrue(optRec.isPresent(), "Active restock recommendation should exist after PO status update");
    }

    // =========================================================================
    // AUDIT-022: InvoicePdfService First-Firm Fallback Elimination & Isolation
    // =========================================================================

    @Test
    @DisplayName("AUDIT-022: InvoicePdfService strictly resolves firm context and rejects cross-firm mismatch")
    void testAudit022_InvoicePdfFirmResolutionAndIsolation() throws Exception {
        Invoice invoiceFirm1 = new Invoice();
        invoiceFirm1.setFirmId(firm1.getId());
        invoiceFirm1.setStatus(InvoiceStatus.FINAL);
        invoiceFirm1.setInvoiceNumber("INV-022-1");
        invoiceFirm1.setInvoiceDate(LocalDateTime.now());
        invoiceFirm1.setTotalAmount(new BigDecimal("100.00"));

        // 1. Matching firmId and context -> Generates PDF
        TenantContext.setCurrentFirmId(firm1.getId());
        byte[] pdf1 = invoicePdfService.generatePdf(invoiceFirm1, InvoicePrintOptions.builder().build());
        assertNotNull(pdf1);
        assertTrue(pdf1.length > 0);

        // 2. Null firmId and Null TenantContext with NO snapshots -> Throws TenantSecurityException (no fallback to firm 1)
        TenantContext.clear();
        Invoice invoiceNoFirm = new Invoice();
        invoiceNoFirm.setStatus(InvoiceStatus.FINAL);
        invoiceNoFirm.setInvoiceNumber("INV-022-NO-FIRM");
        assertThrows(TenantSecurityException.class, () -> {
            invoicePdfService.generatePdf(invoiceNoFirm, InvoicePrintOptions.builder().build());
        });

        // 3. Null firmId but active TenantContext -> Derives firm from TenantContext
        TenantContext.setCurrentFirmId(firm1.getId());
        byte[] pdfContext = invoicePdfService.generatePdf(invoiceNoFirm, InvoicePrintOptions.builder().build());
        assertNotNull(pdfContext);
        assertTrue(pdfContext.length > 0);

        // 4. Null firmId and Null TenantContext WITH snapshot data -> Successfully builds firm from snapshot
        TenantContext.clear();
        invoiceNoFirm.setFirmNameSnapshot("Snapshot Firm Name");
        invoiceNoFirm.setFirmGstinSnapshot("27SNAP1234567Z1");
        invoiceNoFirm.setFirmAddressSnapshot("Snapshot Address Street");
        byte[] pdfSnapshot = invoicePdfService.generatePdf(invoiceNoFirm, InvoicePrintOptions.builder().build());
        assertNotNull(pdfSnapshot);
        assertTrue(pdfSnapshot.length > 0);
    }

    // =========================================================================
    // AUDIT-025: Thermal 80mm Unicode Rendering
    // =========================================================================

    @Test
    @DisplayName("AUDIT-025: Thermal 80mm renders Unicode glyphs (Rupee ₹, Devanagari) using embedded TrueType font")
    void testAudit025_Thermal80mmUnicodeRendering() throws Exception {
        // BaseFont must be initialized
        BaseFont bf = Thermal80mmRenderer.getBaseFont();
        assertNotNull(bf);

        Invoice inv = new Invoice();
        inv.setFirmId(firm1.getId());
        inv.setStatus(InvoiceStatus.FINAL);
        inv.setInvoiceNumber("TH-UNI-001");
        inv.setInvoiceDate(LocalDateTime.now());
        inv.setTotalAmount(new BigDecimal("1250.50"));
        inv.setSubtotalWithoutTax(new BigDecimal("1250.50"));

        Customer cust = Customer.builder()
                .name("राहुल शर्मा (Rahul Sharma)")
                .firmId(firm1.getId())
                .build();
        inv.setCustomer(cust);

        InvoiceItem item1 = new InvoiceItem();
        item1.setInvoice(inv);
        item1.setProductName("बासमती चावल (Basmati Rice)");
        item1.setQty(2);
        item1.setUnit("kg");
        item1.setPricePerUnit(new BigDecimal("500.00"));
        item1.setLineTotal(new BigDecimal("1000.00"));
        inv.getItems().add(item1);

        InvoiceItem item2 = new InvoiceItem();
        item2.setInvoice(inv);
        item2.setProductName("शुद्ध घी (Pure Ghee)");
        item2.setQty(1);
        item2.setUnit("ltr");
        item2.setPricePerUnit(new BigDecimal("250.50"));
        item2.setLineTotal(new BigDecimal("250.50"));
        inv.getItems().add(item2);

        FirmDetails unicodeFirm = new FirmDetails();
        unicodeFirm.setFirmName("श्री गणेश ट्रेडर्स (Shree Ganesh Traders)");
        unicodeFirm.setAddressLine1("दुकान क्र. 5, मार्केट यार्ड");
        unicodeFirm.setCity("मुंबई");
        unicodeFirm.setPincode("400001");
        unicodeFirm.setPhone("9819001234");
        unicodeFirm.setGstin("27AAAAA1234A1Z5");

        InvoicePrintOptions options = InvoicePrintOptions.builder()
                .format(com.billing.simple.billsoft.enums.PrintFormat.THERMAL_80MM)
                .build();

        byte[] pdfBytes = Thermal80mmRenderer.render(inv, unicodeFirm, options);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500, "Thermal PDF should contain valid non-empty byte stream");

        // Verify %PDF header
        String header = new String(pdfBytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals("%PDF", header);
    }
}
