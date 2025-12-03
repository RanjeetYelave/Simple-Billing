package com.billing.simple.billsoft;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.dtos.InvoiceUpdateRequest;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.CustomerRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.service.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // Force H2 in-memory DB for tests, MySQL compatibility mode
                "spring.datasource.url=jdbc:h2:mem:billsoft-test;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false"
        }
)
@ActiveProfiles("test")
class InvoiceServiceTest {

    private static final int SCALE = 2;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepo;

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private ProductRepository productRepo;

    @BeforeEach
    void setUp() {
        // Order matters due to FK constraints
        invoiceRepo.deleteAll();
        customerRepo.deleteAll();
        productRepo.deleteAll();
    }

    // -------------------------
    // Helpers
    // -------------------------
    
    private InvoiceRequest.Discount invoiceValueDiscount(BigDecimal value) {
        InvoiceRequest.Discount d = new InvoiceRequest.Discount();
        d.setType("VALUE"); // ensures service interprets it correctly
        d.setValue(value);  // discount amount
        return d;
    }


    private BigDecimal bd(String v) {
        return new BigDecimal(v).setScale(SCALE, RM);
    }

    private Customer createCustomer(String name) {
        Customer c = Customer.builder()
                .name(name)
                .phone("9999999999")
                .email(name.toLowerCase() + "@example.com")
                .address("Test Address")
                .gstin("TESTGSTIN1234")
                .build();
        return customerRepo.save(c);
    }

    private Product createProduct(String name, String price, String gstPercent) {
        Product p = Product.builder()
                .name(name)
                .price(bd(price))
                .gstPercentage(gstPercent == null ? null : bd(gstPercent))
                .unit("pcs")
                .build();
        return productRepo.save(p);
    }

    private InvoiceRequestItem buildItem(Product p, int qty) {
        InvoiceRequestItem it = new InvoiceRequestItem();
        it.setProductId(p.getId());
        it.setQty(qty);
        it.setUnit(p.getUnit());
        // let backend use product.price and product.gstPercentage
        return it;
    }

    // -------------------------------------------------
    // 1) BASIC CREATION & ESTIMATE
    // -------------------------------------------------

    @Test
    void testCreateInvoiceFinal() {
        Customer c = createCustomer("ACME");
        Product p = createProduct("Widget", "100.00", "18.00");

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(c.getId());
        req.setStatus(InvoiceStatus.FINAL);
        req.setItems(Collections.singletonList(buildItem(p, 1)));

        Invoice inv = invoiceService.createInvoice(req);

        assertNotNull(inv.getId());
        assertNotNull(inv.getInvoiceNumber());
        assertNull(inv.getEstimateNumber());
        assertEquals(InvoiceStatus.FINAL, inv.getStatus());

        // subtotal=100, gst=18, total=118
        assertEquals(bd("100.00"), inv.getSubtotalWithoutTax());
        assertEquals(bd("18.00"), inv.getTotalTax());
        assertEquals(bd("118.00"), inv.getTotalAmount());
    }

    @Test
    void testCreateEstimate() {
        Customer c = createCustomer("Beta");
        Product p = createProduct("Gadget", "200.00", "18.00");

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(c.getId());
        req.setStatus(InvoiceStatus.ESTIMATE);
        req.setItems(Collections.singletonList(buildItem(p, 1)));

        Invoice estimate = invoiceService.createEstimate(req);

        assertNotNull(estimate.getId());
        assertNull(estimate.getInvoiceNumber());
        assertNotNull(estimate.getEstimateNumber());
        assertEquals(InvoiceStatus.ESTIMATE, estimate.getStatus());
        assertFalse(Boolean.TRUE.equals(estimate.getPaid()));

        // Ensure totals make sense
        assertEquals(bd("200.00"), estimate.getSubtotalWithoutTax());
        assertEquals(bd("36.00"), estimate.getTotalTax());
        assertEquals(bd("236.00"), estimate.getTotalAmount());
    }

    // -------------------------------------------------
    // 2) PREVIEW (NO PERSIST)
    // -------------------------------------------------

    @Test
    void testPreviewInvoiceDoesNotSave() {
        Customer c = createCustomer("Preview");
        Product p = createProduct("PreviewItem", "100.00", "18.00");

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(c.getId());
        req.setStatus(InvoiceStatus.FINAL);
        req.setItems(Collections.singletonList(buildItem(p, 1)));

        Invoice preview = invoiceService.previewInvoice(req);

        assertNull(preview.getId(), "Preview invoice must not have an ID (not persisted)");
        assertEquals(bd("100.00"), preview.getSubtotalWithoutTax());
        assertEquals(bd("18.00"), preview.getTotalTax());
        assertEquals(bd("118.00"), preview.getTotalAmount());

        assertEquals(0, invoiceRepo.count(), "No invoice should be stored in DB for preview");
    }

    // -------------------------------------------------
    // 3) UPDATE FULL INVOICE
    // -------------------------------------------------

    @Test
    void testUpdateFullInvoiceOverrideItems() {
        Customer c = createCustomer("UpdateTest");
        Product p1 = createProduct("OldItem", "50.00", "18.00");
        Product p2 = createProduct("NewItem", "200.00", "18.00");

        // create base invoice with 1 line of p1
        InvoiceRequest createReq = new InvoiceRequest();
        createReq.setCustomerId(c.getId());
        createReq.setStatus(InvoiceStatus.FINAL);
        createReq.setItems(Collections.singletonList(buildItem(p1, 2))); // 2 * 50 = 100 + 18% = 118

        Invoice created = invoiceService.createInvoice(createReq);
        Long id = created.getId();

        // now update full with a new line of p2 only
        InvoiceUpdateRequest up = new InvoiceUpdateRequest();
        up.setCustomerId(c.getId());

        InvoiceRequestItem newItem = new InvoiceRequestItem();
        newItem.setProductId(p2.getId());
        newItem.setQty(1);
        newItem.setUnit("pcs");

        List<InvoiceRequestItem> updateItems = new ArrayList<>();
        updateItems.add(newItem);
        up.setItems(updateItems);

        Invoice updated = invoiceService.updateFullInvoice(id, up);

        assertEquals(1, updated.getItems().size(), "Old items must be replaced");
        InvoiceItem onlyItem = updated.getItems().get(0);
        assertEquals(p2.getId(), onlyItem.getProduct().getId());

        // 200 + 18% = 236
        assertEquals(bd("200.00"), updated.getSubtotalWithoutTax());
        assertEquals(bd("36.00"), updated.getTotalTax());
        assertEquals(bd("236.00"), updated.getTotalAmount());
    }

    // -------------------------------------------------
    // 4) UPDATE PAID FLAG
    // -------------------------------------------------

    @Test
    void testUpdatePaidFlag() {
        Customer c = createCustomer("PaidTest");
        Product p = createProduct("PT", "100.00", "18.00");

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(c.getId());
        req.setStatus(InvoiceStatus.FINAL);
        req.setItems(Collections.singletonList(buildItem(p, 1)));

        Invoice created = invoiceService.createInvoice(req);

        assertFalse(Boolean.TRUE.equals(created.getPaid()));
        assertEquals(InvoiceStatus.FINAL, created.getStatus());

        Invoice paid = invoiceService.updatePaidFlag(created.getId(), true);
        assertTrue(paid.getPaid());
        assertEquals(InvoiceStatus.PAID, paid.getStatus());
    }

    // -------------------------------------------------
    // 5) CONVERT ESTIMATE → INVOICE
    // -------------------------------------------------

    @Test
    void testConvertEstimateToInvoice_simple() {
        Customer c = createCustomer("ConvSimple");
        Product p = createProduct("ConvProd", "100.00", "18.00");

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(c.getId());
        req.setStatus(InvoiceStatus.ESTIMATE);
        req.setItems(Collections.singletonList(buildItem(p, 1)));

        Invoice estimate = invoiceService.createEstimate(req);

        Invoice converted = invoiceService.convertEstimateToInvoice(estimate.getId(), null);

        assertNotNull(converted.getId());
        assertEquals(InvoiceStatus.FINAL, converted.getStatus());
        assertNotNull(converted.getInvoiceNumber());
        assertNull(converted.getEstimateNumber(), "Converted invoice should not keep estimate number");

        // amounts must be same
        assertEquals(estimate.getSubtotalWithoutTax(), converted.getSubtotalWithoutTax());
        assertEquals(estimate.getTotalTax(), converted.getTotalTax());
        assertEquals(estimate.getTotalAmount(), converted.getTotalAmount());

        // old estimate must be linked
        Invoice reloadedEstimate = invoiceRepo.findById(estimate.getId()).orElseThrow();
        assertEquals(converted.getId(), reloadedEstimate.getConvertedInvoiceId());
    }

    @Test
    void testConvertEstimateToInvoiceWithOverrideRequest_keepsItemsAndTotals() {
        Customer c = createCustomer("ConvOverride");
        Product p = createProduct("ConvP", "100.00", "18.00");

        // base estimate
        InvoiceRequest createReq = new InvoiceRequest();
        createReq.setCustomerId(c.getId());
        createReq.setStatus(InvoiceStatus.ESTIMATE);
        createReq.setCustomerNote("original-note");
        createReq.setItems(Collections.singletonList(buildItem(p, 1)));

        Invoice estimate = invoiceService.createEstimate(createReq);

        // override only metadata (no items override)
        InvoiceRequest overrideReq = new InvoiceRequest();
        overrideReq.setCustomerId(c.getId());
        overrideReq.setStatus(InvoiceStatus.FINAL);
        overrideReq.setCustomerNote("override-note");
        overrideReq.setDueDate(LocalDate.now().plusDays(30));

        Invoice converted = invoiceService.convertEstimateToInvoice(estimate.getId(), overrideReq);

        // financials must remain same
        assertEquals(estimate.getSubtotalWithoutTax(), converted.getSubtotalWithoutTax());
        assertEquals(estimate.getTotalTax(), converted.getTotalTax());
        assertEquals(estimate.getTotalAmount(), converted.getTotalAmount());

        // metadata overridden
        assertEquals("override-note", converted.getCustomerNote());
        assertEquals(LocalDate.now().plusDays(30), converted.getDueDate());

        // link maintained
        Invoice reloadedEstimate = invoiceRepo.findById(estimate.getId()).orElseThrow();
        assertEquals(converted.getId(), reloadedEstimate.getConvertedInvoiceId());
    }

    // -------------------------------------------------
    // 6) NEW: ZERO + MIXED GST (Scenario 1)
    // -------------------------------------------------

    @Test
    void testCreateInvoice_MixedGstRates_ZeroAndEighteen() {
        Customer c = createCustomer("MixedGST");
        // Item A: GST 0%
        Product p0 = createProduct("GST0", "100.00", "0.00");
        // Item B: GST 18%
        Product p18 = createProduct("GST18", "100.00", "18.00");

        List<InvoiceRequestItem> items = new ArrayList<>();
        items.add(buildItem(p0, 1));
        items.add(buildItem(p18, 1));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(c.getId());
        req.setStatus(InvoiceStatus.FINAL);
        req.setItems(items);

        Invoice inv = invoiceService.createInvoice(req);

        // Without tax: 100 + 100 = 200
        assertEquals(bd("200.00"), inv.getSubtotalWithoutTax());
        // Tax: 0 on first, 18 on second ⇒ 18
        assertEquals(bd("18.00"), inv.getTotalTax());
        // Total: 218
        assertEquals(bd("218.00"), inv.getTotalAmount());

        assertEquals(2, inv.getItems().size());
        // sanity: gstAmount distribution
        InvoiceItem item0 = inv.getItems().stream()
                .filter(it -> "GST0".equals(it.getProduct().getName()))
                .findFirst()
                .orElseThrow();
        InvoiceItem item18 = inv.getItems().stream()
                .filter(it -> "GST18".equals(it.getProduct().getName()))
                .findFirst()
                .orElseThrow();

        assertEquals(bd("0.00"), item0.getGstAmount());
        assertEquals(bd("18.00"), item18.getGstAmount());
    }

    // -------------------------------------------------
    // 7) NEW: Invoice-Level Discount Split (Scenario 2)
    // -------------------------------------------------

    @Test
    void testCreateInvoice_InvoiceLevelDiscountSplitProportionally() {
        Customer c = createCustomer("InvDiscSplit");
        // Item1: 100, Item2: 300, both 18% GST
        Product p1 = createProduct("P1", "100.00", "18.00");
        Product p2 = createProduct("P2", "300.00", "18.00");

        InvoiceRequestItem it1 = buildItem(p1, 1);
        InvoiceRequestItem it2 = buildItem(p2, 1);
        List<InvoiceRequestItem> items = List.of(it1, it2);

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(c.getId());
        req.setStatus(InvoiceStatus.FINAL);
        req.setItems(items);

        // 🔴 OLD (causing compilation error):
        // req.setInvoiceDiscount(bd("100.00"));

        // ✅ NEW: use inner Discount object
        req.setInvoiceDiscount(invoiceValueDiscount(bd("100.00")));

        Invoice inv = invoiceService.createInvoice(req);

        // Base subtotal = 100 + 300 = 400
        assertEquals(bd("400.00"), inv.getSubtotalWithoutTax());

        // Discount should be fully accounted as 100
        assertEquals(bd("100.00"), inv.getTotalDiscount());

        // After discount subtotal = 300
        // P1: base 100 -> 25% of discount -> 25, taxable = 75, gst = 13.50
        // P2: base 300 -> 75% of discount -> 75, taxable = 225, gst = 40.50
        // Total tax = 54, total amount = 354
        assertEquals(bd("54.00"), inv.getTotalTax());
        assertEquals(bd("354.00"), inv.getTotalAmount());

        // Verify per-line invariants
        InvoiceItem item1 = inv.getItems().stream()
                .filter(it -> "P1".equals(it.getProduct().getName()))
                .findFirst().orElseThrow();
        InvoiceItem item2 = inv.getItems().stream()
                .filter(it -> "P2".equals(it.getProduct().getName()))
                .findFirst().orElseThrow();

        assertEquals(bd("100.00"), item1.getAmountWithoutTax());
        assertEquals(bd("25.00"), item1.getDiscountValue());
        assertEquals(bd("75.00"), item1.getTaxableAmount());
        assertEquals(bd("13.50"), item1.getGstAmount());
        assertEquals(bd("88.50"), item1.getLineTotal());

        assertEquals(bd("300.00"), item2.getAmountWithoutTax());
        assertEquals(bd("75.00"), item2.getDiscountValue());
        assertEquals(bd("225.00"), item2.getTaxableAmount());
        assertEquals(bd("40.50"), item2.getGstAmount());
        assertEquals(bd("265.50"), item2.getLineTotal());

        BigDecimal sumLines = item1.getLineTotal().add(item2.getLineTotal()).setScale(2, RoundingMode.HALF_UP);
        assertEquals(inv.getTotalAmount(), sumLines);
    }

    // -------------------------------------------------
    // 8) NEW: Illegal Discount Clamped (Scenario 4)
    // -------------------------------------------------

    @Test
    void testCreateInvoice_IllegalLineDiscountClampedToLineAmount() {
        Customer c = createCustomer("IllegalDisc");
        Product p = createProduct("ClampProd", "100.00", "18.00");

        InvoiceRequestItem it = new InvoiceRequestItem();
        it.setProductId(p.getId());
        it.setQty(1);
        it.setUnit("pcs");
        // Intentionally send discount greater than line amount
        it.setDiscountValue(bd("150.00"));

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerId(c.getId());
        req.setStatus(InvoiceStatus.FINAL);
        req.setItems(Collections.singletonList(it));

        Invoice inv = invoiceService.createInvoice(req);

        assertEquals(1, inv.getItems().size());
        InvoiceItem item = inv.getItems().get(0);

        // Engine should clamp discount to line base (100),
        // so no negative taxable/amount.
        assertEquals(bd("100.00"), item.getAmountWithoutTax());
        assertEquals(bd("100.00"), item.getDiscountValue());
        assertEquals(bd("0.00"), item.getTaxableAmount());
        assertEquals(bd("0.00"), item.getGstAmount());
        assertEquals(bd("0.00"), item.getLineTotal());

        // Invoice totals must also be non-negative and consistent
        assertEquals(bd("100.00"), inv.getSubtotalWithoutTax());
        assertEquals(bd("100.00"), inv.getTotalDiscount());
        assertEquals(bd("0.00"), inv.getTotalTax());
        assertEquals(bd("0.00"), inv.getTotalAmount());
    }
}
