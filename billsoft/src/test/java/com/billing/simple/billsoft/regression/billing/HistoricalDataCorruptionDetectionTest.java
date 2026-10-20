package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.engine.InvoiceCalculationEngine;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("regression")
@Tag("unit")
@DisplayName("Forensic Historical Data Corruption & Detection Signature Test")
class HistoricalDataCorruptionDetectionTest {

    private InvoiceCalculationEngine engine;
    private Customer mockCustomer;
    private List<Product> mockProducts;

    @BeforeEach
    void setUp() {
        engine = new InvoiceCalculationEngine();
        mockCustomer = Customer.builder()
                .id(1L)
                .name("Test Customer")
                .firmId(1L)
                .build();
        mockProducts = new ArrayList<>();
    }

    private Product createProduct(Long id, String name, double price, double gst) {
        Product p = Product.builder()
                .id(id)
                .name(name)
                .price(BigDecimal.valueOf(price))
                .gstPercentage(BigDecimal.valueOf(gst))
                .firmId(1L)
                .itemType("GOODS")
                .unit("pcs")
                .build();
        mockProducts.add(p);
        return p;
    }

    /**
     * Simulates the exact legacy calculation engine algorithm prior to Stage 2.1
     * which mutated item.discountValue with the pro-rata invoice discount share.
     */
    private Invoice simulateLegacyCalculation(InvoiceRequest request) {
        Invoice invoice = new Invoice();
        invoice.setFirmId(request.getFirmId());
        invoice.setCustomer(mockCustomer);

        List<InvoiceItem> items = new ArrayList<>();
        for (InvoiceRequestItem ri : request.getItems()) {
            InvoiceItem item = new InvoiceItem();
            BigDecimal amountNoTax = ri.getPricePerUnit().multiply(BigDecimal.valueOf(ri.getQty())).setScale(2, RoundingMode.HALF_UP);
            item.setPricePerUnit(ri.getPricePerUnit());
            item.setQty(ri.getQty());
            item.setAmountWithoutTax(amountNoTax);
            BigDecimal dval = ri.getDiscountValue() != null ? ri.getDiscountValue().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            item.setDiscountValue(dval);
            item.setTaxableAmount(amountNoTax.subtract(dval));
            item.setGstPercent(ri.getGstPercent());
            item.setInvoice(invoice);
            items.add(item);
        }
        invoice.setItems(items);

        BigDecimal rawSubtotal = BigDecimal.ZERO;
        BigDecimal itemDiscountSum = BigDecimal.ZERO;
        for (InvoiceItem it : items) {
            rawSubtotal = rawSubtotal.add(it.getAmountWithoutTax());
            itemDiscountSum = itemDiscountSum.add(it.getDiscountValue());
        }

        BigDecimal taxableBeforeInvDisc = BigDecimal.ZERO;
        for (InvoiceItem it : items) {
            taxableBeforeInvDisc = taxableBeforeInvDisc.add(it.getTaxableAmount());
        }

        BigDecimal invoiceDiscountAmt = BigDecimal.ZERO;
        if (request.getInvoiceDiscount() != null && request.getInvoiceDiscount().getValue() != null) {
            if ("PERCENT".equalsIgnoreCase(request.getInvoiceDiscount().getType())) {
                invoiceDiscountAmt = taxableBeforeInvDisc.multiply(request.getInvoiceDiscount().getValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else {
                invoiceDiscountAmt = request.getInvoiceDiscount().getValue().setScale(2, RoundingMode.HALF_UP);
            }
            invoice.setInvoiceDiscountType(request.getInvoiceDiscount().getType());
            invoice.setInvoiceDiscountValue(request.getInvoiceDiscount().getValue());
        }

        // Pro-rata distribution WITH the legacy mutation
        if (invoiceDiscountAmt.compareTo(BigDecimal.ZERO) > 0 && taxableBeforeInvDisc.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remaining = invoiceDiscountAmt;
            for (int idx = 0; idx < items.size(); idx++) {
                InvoiceItem it = items.get(idx);
                BigDecimal itemTaxable = it.getTaxableAmount();
                if (idx == items.size() - 1) {
                    BigDecimal share = remaining;
                    it.setTaxableAmount(itemTaxable.subtract(share));
                    it.setDiscountValue(it.getDiscountValue().add(share)); // LEGACY MUTATION
                    remaining = BigDecimal.ZERO;
                } else {
                    BigDecimal share = itemTaxable.multiply(invoiceDiscountAmt)
                            .divide(taxableBeforeInvDisc, 2, RoundingMode.HALF_UP);
                    if (share.compareTo(remaining) > 0) share = remaining;
                    it.setTaxableAmount(itemTaxable.subtract(share));
                    it.setDiscountValue(it.getDiscountValue().add(share)); // LEGACY MUTATION
                    remaining = remaining.subtract(share);
                }
            }
        }

        BigDecimal gstTotal = BigDecimal.ZERO;
        for (InvoiceItem it : items) {
            BigDecimal taxable = it.getTaxableAmount();
            BigDecimal gst = taxable.multiply(it.getGstPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            it.setGstAmount(gst);
            it.setLineTotal(taxable.add(gst));
            gstTotal = gstTotal.add(gst);
        }

        invoice.setSubtotalWithoutTax(rawSubtotal);
        invoice.setTotalDiscount(itemDiscountSum.add(invoiceDiscountAmt));
        invoice.setTotalTax(gstTotal);
        invoice.setTotalAmount(rawSubtotal.subtract(invoice.getTotalDiscount()).add(gstTotal));
        return invoice;
    }

    /**
     * Detection Signature Classifier
     */
    enum CorruptionStatus {
        DEFINITELY_VALID_NO_INVOICE_DISCOUNT,
        DEFINITELY_VALID_POST_REMEDIATION,
        SUSPECT_CORRUPTED_SINGLE_PASS_NO_USER_DISCOUNT,
        AMBIGUOUS_MULTI_PASS_OR_MIXED_DISCOUNT
    }

    private CorruptionStatus classifyInvoice(Invoice inv) {
        BigDecimal invDiscVal = inv.getInvoiceDiscountValue();
        if (invDiscVal == null || invDiscVal.compareTo(BigDecimal.ZERO) == 0) {
            return CorruptionStatus.DEFINITELY_VALID_NO_INVOICE_DISCOUNT;
        }

        BigDecimal sumItemDiscounts = inv.getItems().stream()
                .map(it -> it.getDiscountValue() != null ? it.getDiscountValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDiscount = inv.getTotalDiscount() != null ? inv.getTotalDiscount() : BigDecimal.ZERO;

        // Post-remediation invariant: totalDiscount = sumItemDiscounts + invoiceDiscountAmt
        // Thus totalDiscount > sumItemDiscounts by exactly the invoiceDiscountAmt
        if (totalDiscount.compareTo(sumItemDiscounts) > 0) {
            return CorruptionStatus.DEFINITELY_VALID_POST_REMEDIATION;
        }

        // Corrupted invariant: sumItemDiscounts == totalDiscount
        if (sumItemDiscounts.compareTo(totalDiscount) == 0) {
            // If totalDiscount == invoiceDiscountValue (for VALUE type), then original item discounts were definitely 0
            if ("VALUE".equalsIgnoreCase(inv.getInvoiceDiscountType()) && totalDiscount.compareTo(invDiscVal) == 0) {
                return CorruptionStatus.SUSPECT_CORRUPTED_SINGLE_PASS_NO_USER_DISCOUNT;
            } else {
                return CorruptionStatus.AMBIGUOUS_MULTI_PASS_OR_MIXED_DISCOUNT;
            }
        }

        return CorruptionStatus.AMBIGUOUS_MULTI_PASS_OR_MIXED_DISCOUNT;
    }

    @Test
    @DisplayName("Scenario 1: No invoice-level discount is DEFINITELY_VALID")
    void scenario1_noInvoiceDiscount_isDefinitelyValid() {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setQty(2);
        it1.setPricePerUnit(BigDecimal.valueOf(100.00));
        it1.setDiscountValue(BigDecimal.valueOf(10.00));
        it1.setGstPercent(BigDecimal.valueOf(18.0));
        req.setItems(List.of(it1));

        Invoice legacyInv = simulateLegacyCalculation(req);
        assertThat(classifyInvoice(legacyInv)).isEqualTo(CorruptionStatus.DEFINITELY_VALID_NO_INVOICE_DISCOUNT);
    }

    @Test
    @DisplayName("Scenario 2: Fixed invoice discount created with Stage 2.1 engine is DEFINITELY_VALID_POST_REMEDIATION")
    void scenario2_postRemediationFixedDiscount_isDefinitelyValid() {
        Product p1 = createProduct(1L, "P1", 100.0, 0.0);
        Product p2 = createProduct(2L, "P2", 100.0, 0.0);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setProductId(p1.getId());
        it1.setQty(1);
        it1.setPricePerUnit(BigDecimal.valueOf(100.00));
        it1.setDiscountValue(BigDecimal.ZERO);
        it1.setGstPercent(BigDecimal.ZERO);

        InvoiceRequestItem it2 = new InvoiceRequestItem();
        it2.setProductId(p2.getId());
        it2.setQty(1);
        it2.setPricePerUnit(BigDecimal.valueOf(100.00));
        it2.setDiscountValue(BigDecimal.ZERO);
        it2.setGstPercent(BigDecimal.ZERO);

        req.setItems(List.of(it1, it2));
        req.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(50.00)));

        Invoice cleanInv = engine.calculate(new Invoice(), mockCustomer, mockProducts, req, false);
        assertThat(classifyInvoice(cleanInv)).isEqualTo(CorruptionStatus.DEFINITELY_VALID_POST_REMEDIATION);
    }

    @Test
    @DisplayName("Scenario 3: Legacy 1-pass fixed invoice discount with no item discounts is SUSPECT_CORRUPTED_SINGLE_PASS")
    void scenario3_legacySinglePassFixedDiscount_isDetected() {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setQty(1);
        it1.setPricePerUnit(BigDecimal.valueOf(100.00));
        it1.setDiscountValue(BigDecimal.ZERO);
        it1.setGstPercent(BigDecimal.ZERO);

        InvoiceRequestItem it2 = new InvoiceRequestItem();
        it2.setQty(1);
        it2.setPricePerUnit(BigDecimal.valueOf(100.00));
        it2.setDiscountValue(BigDecimal.ZERO);
        it2.setGstPercent(BigDecimal.ZERO);

        req.setItems(List.of(it1, it2));
        req.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(50.00)));

        Invoice legacyInv = simulateLegacyCalculation(req);

        // In legacy, each item got 25.00 discountValue, totalDiscount = 50.00
        assertThat(legacyInv.getItems().get(0).getDiscountValue()).isEqualByComparingTo("25.00");
        assertThat(legacyInv.getItems().get(1).getDiscountValue()).isEqualByComparingTo("25.00");
        assertThat(legacyInv.getTotalDiscount()).isEqualByComparingTo("50.00");

        assertThat(classifyInvoice(legacyInv)).isEqualTo(CorruptionStatus.SUSPECT_CORRUPTED_SINGLE_PASS_NO_USER_DISCOUNT);
    }

    @Test
    @DisplayName("Scenario 4: Legacy mixed discount (user item discount + invoice discount) is AMBIGUOUS")
    void scenario4_legacyMixedDiscount_isAmbiguous() {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setQty(1);
        it1.setPricePerUnit(BigDecimal.valueOf(100.00));
        it1.setDiscountValue(BigDecimal.valueOf(20.00)); // user discount 20
        it1.setGstPercent(BigDecimal.ZERO);

        InvoiceRequestItem it2 = new InvoiceRequestItem();
        it2.setQty(1);
        it2.setPricePerUnit(BigDecimal.valueOf(100.00));
        it2.setDiscountValue(BigDecimal.ZERO);
        it2.setGstPercent(BigDecimal.ZERO);

        req.setItems(List.of(it1, it2));
        req.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(18.00)));

        Invoice legacyInv = simulateLegacyCalculation(req);

        // Total discount is 20 + 18 = 38.00. But invoiceDiscountValue is 18.00.
        assertThat(legacyInv.getTotalDiscount()).isEqualByComparingTo("38.00");
        assertThat(classifyInvoice(legacyInv)).isEqualTo(CorruptionStatus.AMBIGUOUS_MULTI_PASS_OR_MIXED_DISCOUNT);
    }

    @Test
    @DisplayName("Scenario 5: Multi-pass compounded invoice produces identical state to legitimate higher discount (Irreversible Ambiguity)")
    void scenario5_multipassCompoundingAmbiguity() {
        // Invoice A: Originally NO item discount, updated twice in legacy engine (Pass 1 then Pass 2)
        InvoiceRequest reqA = new InvoiceRequest();
        reqA.setFirmId(1L);
        InvoiceRequestItem a1 = new InvoiceRequestItem();
        a1.setQty(1);
        a1.setPricePerUnit(BigDecimal.valueOf(100.00));
        a1.setDiscountValue(BigDecimal.ZERO);
        a1.setGstPercent(BigDecimal.ZERO);

        InvoiceRequestItem a2 = new InvoiceRequestItem();
        a2.setQty(1);
        a2.setPricePerUnit(BigDecimal.valueOf(100.00));
        a2.setDiscountValue(BigDecimal.ZERO);
        a2.setGstPercent(BigDecimal.ZERO);

        reqA.setItems(List.of(a1, a2));
        reqA.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(19.00)));

        Invoice pass1 = simulateLegacyCalculation(reqA);
        // Pass 1 produces items with 9.50 each

        // Pass 2: form reloaded with 9.50 each
        InvoiceRequest reqA_pass2 = new InvoiceRequest();
        reqA_pass2.setFirmId(1L);
        InvoiceRequestItem a1_p2 = new InvoiceRequestItem();
        a1_p2.setQty(1);
        a1_p2.setPricePerUnit(BigDecimal.valueOf(100.00));
        a1_p2.setDiscountValue(pass1.getItems().get(0).getDiscountValue()); // 9.50
        a1_p2.setGstPercent(BigDecimal.ZERO);

        InvoiceRequestItem a2_p2 = new InvoiceRequestItem();
        a2_p2.setQty(1);
        a2_p2.setPricePerUnit(BigDecimal.valueOf(100.00));
        a2_p2.setDiscountValue(pass1.getItems().get(1).getDiscountValue()); // 9.50
        a2_p2.setGstPercent(BigDecimal.ZERO);

        reqA_pass2.setItems(List.of(a1_p2, a2_p2));
        reqA_pass2.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(19.00)));

        Invoice pass2 = simulateLegacyCalculation(reqA_pass2);
        // Pass 2 produces:
        // Item 1 discount = 19.00
        // Item 2 discount = 19.00
        // totalDiscount = 38.00
        assertThat(pass2.getItems().get(0).getDiscountValue()).isEqualByComparingTo("19.00");
        assertThat(pass2.getItems().get(1).getDiscountValue()).isEqualByComparingTo("19.00");
        assertThat(pass2.getTotalDiscount()).isEqualByComparingTo("38.00");

        // Now Invoice B: Legitimate user item discount of 9.50 on each item, created once (1 pass)
        InvoiceRequest reqB = new InvoiceRequest();
        reqB.setFirmId(1L);
        InvoiceRequestItem b1 = new InvoiceRequestItem();
        b1.setQty(1);
        b1.setPricePerUnit(BigDecimal.valueOf(100.00));
        b1.setDiscountValue(BigDecimal.valueOf(9.50)); // user entered 9.50
        b1.setGstPercent(BigDecimal.ZERO);

        InvoiceRequestItem b2 = new InvoiceRequestItem();
        b2.setQty(1);
        b2.setPricePerUnit(BigDecimal.valueOf(100.00));
        b2.setDiscountValue(BigDecimal.valueOf(9.50)); // user entered 9.50
        b2.setGstPercent(BigDecimal.ZERO);

        reqB.setItems(List.of(b1, b2));
        reqB.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(19.00)));

        Invoice invB = simulateLegacyCalculation(reqB);

        // Assert: The persisted state of Invoice A (Pass 2) and Invoice B (Pass 1) is IDENTICAL in every field!
        assertThat(pass2.getItems().get(0).getDiscountValue()).isEqualTo(invB.getItems().get(0).getDiscountValue());
        assertThat(pass2.getItems().get(1).getDiscountValue()).isEqualTo(invB.getItems().get(1).getDiscountValue());
        assertThat(pass2.getTotalDiscount()).isEqualTo(invB.getTotalDiscount());
        assertThat(pass2.getSubtotalWithoutTax()).isEqualTo(invB.getSubtotalWithoutTax());
        assertThat(pass2.getInvoiceDiscountValue()).isEqualTo(invB.getInvoiceDiscountValue());

        // Conclusion: An automated backfill script CANNOT distinguish Invoice A (original discount 0)
        // from Invoice B (original discount 9.50). Proves UNSAFE classification.
    }

    @Test
    @DisplayName("Scenario 6: Percentage invoice discount under legacy engine corrupts and compounds non-linearly")
    void scenario6_percentageInvoiceDiscount_legacyCorruptionAndDetection() {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setQty(1);
        it1.setPricePerUnit(BigDecimal.valueOf(100.00));
        it1.setDiscountValue(BigDecimal.ZERO);
        it1.setGstPercent(BigDecimal.ZERO);

        InvoiceRequestItem it2 = new InvoiceRequestItem();
        it2.setQty(1);
        it2.setPricePerUnit(BigDecimal.valueOf(100.00));
        it2.setDiscountValue(BigDecimal.ZERO);
        it2.setGstPercent(BigDecimal.ZERO);

        req.setItems(List.of(it1, it2));
        req.setInvoiceDiscount(new InvoiceRequest.Discount("PERCENT", BigDecimal.valueOf(10.0))); // 10%

        Invoice pass1 = simulateLegacyCalculation(req);
        // Pass 1: 10% of 200 = 20.00 discount. Each item gets 10.00.
        assertThat(pass1.getItems().get(0).getDiscountValue()).isEqualByComparingTo("10.00");
        assertThat(pass1.getItems().get(1).getDiscountValue()).isEqualByComparingTo("10.00");
        assertThat(pass1.getTotalDiscount()).isEqualByComparingTo("20.00");

        // Pass 2: form reloaded with item discounts = 10.00 each
        InvoiceRequest req_pass2 = new InvoiceRequest();
        req_pass2.setFirmId(1L);
        InvoiceRequestItem p2_it1 = new InvoiceRequestItem();
        p2_it1.setQty(1);
        p2_it1.setPricePerUnit(BigDecimal.valueOf(100.00));
        p2_it1.setDiscountValue(pass1.getItems().get(0).getDiscountValue()); // 10.00
        p2_it1.setGstPercent(BigDecimal.ZERO);

        InvoiceRequestItem p2_it2 = new InvoiceRequestItem();
        p2_it2.setQty(1);
        p2_it2.setPricePerUnit(BigDecimal.valueOf(100.00));
        p2_it2.setDiscountValue(pass1.getItems().get(1).getDiscountValue()); // 10.00
        p2_it2.setGstPercent(BigDecimal.ZERO);

        req_pass2.setItems(List.of(p2_it1, p2_it2));
        req_pass2.setInvoiceDiscount(new InvoiceRequest.Discount("PERCENT", BigDecimal.valueOf(10.0)));

        Invoice pass2 = simulateLegacyCalculation(req_pass2);
        // In pass 2: taxableBeforeInvDisc was 180.00. 10% was 18.00!
        // Each item got 10.00 + 9.00 = 19.00!
        // Total discount became (10 + 10) + 18 = 38.00!
        assertThat(pass2.getItems().get(0).getDiscountValue()).isEqualByComparingTo("19.00");
        assertThat(pass2.getItems().get(1).getDiscountValue()).isEqualByComparingTo("19.00");
        assertThat(pass2.getTotalDiscount()).isEqualByComparingTo("38.00");

        // Proves non-linear decay: in pass 1 discount was 20.00; in pass 2 total discount is 38.00;
        // The effective discount rate became 19% instead of 10%.
    }

    @Test
    @DisplayName("Scenario 7: Zero and small taxable items under pro-rata distribution")
    void scenario7_zeroAndSmallTaxableItems_distributionBehavior() {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setQty(1);
        it1.setPricePerUnit(BigDecimal.valueOf(0.00)); // zero amount item (e.g. free sample)
        it1.setDiscountValue(BigDecimal.ZERO);
        it1.setGstPercent(BigDecimal.ZERO);

        InvoiceRequestItem it2 = new InvoiceRequestItem();
        it2.setQty(1);
        it2.setPricePerUnit(BigDecimal.valueOf(100.00));
        it2.setDiscountValue(BigDecimal.ZERO);
        it2.setGstPercent(BigDecimal.ZERO);

        req.setItems(List.of(it1, it2));
        req.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(10.00)));

        Invoice legacyInv = simulateLegacyCalculation(req);

        // Item 1 has 0 taxable -> gets 0 share
        assertThat(legacyInv.getItems().get(0).getDiscountValue()).isEqualByComparingTo("0.00");
        // Item 2 has 100 taxable -> gets all 10.00 share
        assertThat(legacyInv.getItems().get(1).getDiscountValue()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("Scenario 8: Rounding and remainder absorption causes asymmetry on last item")
    void scenario8_roundingAndRemainderAbsorption_lastItemAsymmetry() {
        // 3 items @ 100.00 = 300.00 total. Invoice discount = 10.00.
        // Pro-rata share: 100 * 10 / 300 = 3.3333 -> 3.33 each.
        // Last item absorbs remainder: 10.00 - 3.33 - 3.33 = 3.34.
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setQty(1);
        it1.setPricePerUnit(BigDecimal.valueOf(100.00));
        it1.setDiscountValue(BigDecimal.ZERO);
        it1.setGstPercent(BigDecimal.ZERO);

        InvoiceRequestItem it2 = new InvoiceRequestItem();
        it2.setQty(1);
        it2.setPricePerUnit(BigDecimal.valueOf(100.00));
        it2.setDiscountValue(BigDecimal.ZERO);
        it2.setGstPercent(BigDecimal.ZERO);

        InvoiceRequestItem it3 = new InvoiceRequestItem();
        it3.setQty(1);
        it3.setPricePerUnit(BigDecimal.valueOf(100.00));
        it3.setDiscountValue(BigDecimal.ZERO);
        it3.setGstPercent(BigDecimal.ZERO);

        req.setItems(List.of(it1, it2, it3));
        req.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(10.00)));

        Invoice legacyInv = simulateLegacyCalculation(req);

        assertThat(legacyInv.getItems().get(0).getDiscountValue()).isEqualByComparingTo("3.33");
        assertThat(legacyInv.getItems().get(1).getDiscountValue()).isEqualByComparingTo("3.33");
        assertThat(legacyInv.getItems().get(2).getDiscountValue()).isEqualByComparingTo("3.34"); // absorbs remainder

        // Proves that even with identical item prices and quantities, item discountValues are NOT equal due to remainder distribution
    }

    @Test
    @DisplayName("Scenario 9: Estimate to Invoice conversion under legacy algorithm baked discount into converted invoice items")
    void scenario9_estimateToInvoiceConversion_historicalState() {
        // In legacy code, estimate conversion cloned items with their mutated discountValue
        InvoiceRequest estReq = new InvoiceRequest();
        estReq.setFirmId(1L);
        InvoiceRequestItem it1 = new InvoiceRequestItem();
        it1.setQty(1);
        it1.setPricePerUnit(BigDecimal.valueOf(100.00));
        it1.setDiscountValue(BigDecimal.ZERO);
        it1.setGstPercent(BigDecimal.ZERO);

        estReq.setItems(List.of(it1));
        estReq.setInvoiceDiscount(new InvoiceRequest.Discount("VALUE", BigDecimal.valueOf(15.00)));

        Invoice legacyEst = simulateLegacyCalculation(estReq);
        assertThat(legacyEst.getItems().get(0).getDiscountValue()).isEqualByComparingTo("15.00");

        // When converted in legacy code, converted invoice items inherited the 15.00 discountValue
        // which now appears as a persisted item discount in invoice_items table.
    }

    @Test
    @DisplayName("Verify read-only SQL detection query on H2 relational database")
    void verifyReadOnlySqlDetectionQuery() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:sqldetectiontest;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE invoices (" +
                        "id BIGINT PRIMARY KEY, " +
                        "invoice_number VARCHAR(50), " +
                        "invoice_date TIMESTAMP, " +
                        "invoice_discount_type VARCHAR(20), " +
                        "invoice_discount_value NUMERIC(15,2), " +
                        "total_discount NUMERIC(15,2), " +
                        "subtotal_without_tax NUMERIC(15,2))");

                st.execute("CREATE TABLE invoice_items (" +
                        "id BIGINT PRIMARY KEY, " +
                        "invoice_id BIGINT, " +
                        "product_name VARCHAR(255), " +
                        "amount_without_tax NUMERIC(15,2), " +
                        "discount_value NUMERIC(15,2), " +
                        "taxable_amount NUMERIC(15,2))");

                // Row 1: Definitely valid (no invoice discount)
                st.execute("INSERT INTO invoices VALUES (1, 'INV-001', CURRENT_TIMESTAMP, NULL, NULL, 10.00, 100.00)");
                st.execute("INSERT INTO invoice_items VALUES (101, 1, 'Item 1', 100.00, 10.00, 90.00)");

                // Row 2: Definitely valid (post-remediation clean)
                st.execute("INSERT INTO invoices VALUES (2, 'INV-002', CURRENT_TIMESTAMP, 'VALUE', 50.00, 50.00, 200.00)");
                st.execute("INSERT INTO invoice_items VALUES (201, 2, 'Item 1', 100.00, 0.00, 75.00)");
                st.execute("INSERT INTO invoice_items VALUES (202, 2, 'Item 2', 100.00, 0.00, 75.00)");

                // Row 3: Suspect legacy single-pass (no user discounts)
                st.execute("INSERT INTO invoices VALUES (3, 'INV-003', CURRENT_TIMESTAMP, 'VALUE', 50.00, 50.00, 200.00)");
                st.execute("INSERT INTO invoice_items VALUES (301, 3, 'Item 1', 100.00, 25.00, 75.00)");
                st.execute("INSERT INTO invoice_items VALUES (302, 3, 'Item 2', 100.00, 25.00, 75.00)");

                // Row 4: Ambiguous (multi-pass or mixed)
                st.execute("INSERT INTO invoices VALUES (4, 'INV-004', CURRENT_TIMESTAMP, 'VALUE', 50.00, 100.00, 200.00)");
                st.execute("INSERT INTO invoice_items VALUES (401, 4, 'Item 1', 100.00, 50.00, 50.00)");
                st.execute("INSERT INTO invoice_items VALUES (402, 4, 'Item 2', 100.00, 50.00, 50.00)");

                String query = "SELECT " +
                        "    i.id AS invoice_id, " +
                        "    i.invoice_number, " +
                        "    i.invoice_date, " +
                        "    i.invoice_discount_type, " +
                        "    i.invoice_discount_value, " +
                        "    i.total_discount, " +
                        "    i.subtotal_without_tax, " +
                        "    item.id AS item_id, " +
                        "    item.product_name, " +
                        "    item.amount_without_tax, " +
                        "    item.discount_value AS item_discount_value, " +
                        "    item.taxable_amount AS item_taxable_amount, " +
                        "    item_agg.sum_item_discounts, " +
                        "    CASE " +
                        "        WHEN i.invoice_discount_value IS NULL OR i.invoice_discount_value = 0 THEN " +
                        "            'DEFINITELY_VALID_NO_INVOICE_DISCOUNT' " +
                        "        WHEN i.invoice_discount_value > 0 AND (i.total_discount - item_agg.sum_item_discounts) > 0 THEN " +
                        "            'DEFINITELY_VALID_POST_REMEDIATION' " +
                        "        WHEN i.invoice_discount_value > 0 " +
                        "             AND UPPER(i.invoice_discount_type) = 'VALUE' " +
                        "             AND i.total_discount = i.invoice_discount_value " +
                        "             AND item_agg.sum_item_discounts = i.total_discount THEN " +
                        "            'SUSPECT_CORRUPTED_SINGLE_PASS_NO_USER_DISCOUNT' " +
                        "        ELSE " +
                        "            'AMBIGUOUS_MULTI_PASS_OR_MIXED_DISCOUNT' " +
                        "    END AS corruption_classification " +
                        "FROM invoices i " +
                        "JOIN invoice_items item ON item.invoice_id = i.id " +
                        "JOIN ( " +
                        "    SELECT invoice_id, SUM(COALESCE(discount_value, 0)) AS sum_item_discounts " +
                        "    FROM invoice_items " +
                        "    GROUP BY invoice_id " +
                        ") item_agg ON item_agg.invoice_id = i.id " +
                        "ORDER BY i.id, item.id";

                try (ResultSet rs = st.executeQuery(query)) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong("invoice_id")).isEqualTo(1L);
                    assertThat(rs.getString("corruption_classification")).isEqualTo("DEFINITELY_VALID_NO_INVOICE_DISCOUNT");

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong("invoice_id")).isEqualTo(2L);
                    assertThat(rs.getString("corruption_classification")).isEqualTo("DEFINITELY_VALID_POST_REMEDIATION");

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong("invoice_id")).isEqualTo(2L);
                    assertThat(rs.getString("corruption_classification")).isEqualTo("DEFINITELY_VALID_POST_REMEDIATION");

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong("invoice_id")).isEqualTo(3L);
                    assertThat(rs.getString("corruption_classification")).isEqualTo("SUSPECT_CORRUPTED_SINGLE_PASS_NO_USER_DISCOUNT");

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong("invoice_id")).isEqualTo(3L);
                    assertThat(rs.getString("corruption_classification")).isEqualTo("SUSPECT_CORRUPTED_SINGLE_PASS_NO_USER_DISCOUNT");

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong("invoice_id")).isEqualTo(4L);
                    assertThat(rs.getString("corruption_classification")).isEqualTo("AMBIGUOUS_MULTI_PASS_OR_MIXED_DISCOUNT");

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong("invoice_id")).isEqualTo(4L);
                    assertThat(rs.getString("corruption_classification")).isEqualTo("AMBIGUOUS_MULTI_PASS_OR_MIXED_DISCOUNT");

                    assertThat(rs.next()).isFalse();
                }
            }
        }
    }
}
