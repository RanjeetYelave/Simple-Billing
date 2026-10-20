package com.billing.simple.billsoft.regression.billing;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.engine.InvoiceCalculationEngine;
import com.billing.simple.billsoft.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("regression")
@Tag("unit")
@DisplayName("Invoice Calculation Engine Regression Tests")
class InvoiceCalculationRegressionTest {

    private InvoiceCalculationEngine engine;
    private Customer mockCustomer;
    private List<Product> mockProducts;

    @BeforeEach
    void setUp() {
        engine = new InvoiceCalculationEngine();
        mockCustomer = Customer.builder()
                .id(1L)
                .name("Alpha Enterprises")
                .firmId(1L)
                .gstin("27AAAAA0000A1Z5")
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

    @Test
    @DisplayName("Should accurately calculate invoice with single item, 18% GST, and no discount")
    void shouldCalculateSingleItemWithGst() {
        Product p = createProduct(101L, "Standard Widget", 100.00, 18.00);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        req.setCustomerId(1L);
        req.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(p.getId());
        item.setQty(2);
        item.setPricePerUnit(BigDecimal.valueOf(100.00));
        item.setGstPercent(BigDecimal.valueOf(18.00));
        req.setItems(List.of(item));

        Invoice inv = engine.calculate(new Invoice(), mockCustomer, mockProducts, req, false);

        assertThat(inv.getSubtotalWithoutTax()).isEqualByComparingTo("200.00");
        assertThat(inv.getTotalDiscount()).isEqualByComparingTo("0.00");
        assertThat(inv.getTotalTax()).isEqualByComparingTo("36.00"); // 18% of 200
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("236.00");
    }

    @Test
    @DisplayName("Should accurately calculate item-level value discounts before tax")
    void shouldCalculateItemLevelDiscountBeforeTax() {
        Product p = createProduct(102L, "Discounted Gadget", 500.00, 18.00);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        req.setCustomerId(1L);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(p.getId());
        item.setQty(2); // total 1000
        item.setPricePerUnit(BigDecimal.valueOf(500.00));
        item.setDiscountValue(BigDecimal.valueOf(100.00)); // 1000 - 100 = 900 taxable
        item.setGstPercent(BigDecimal.valueOf(18.00)); // 18% of 900 = 162
        req.setItems(List.of(item));

        Invoice inv = engine.calculate(new Invoice(), mockCustomer, mockProducts, req, false);

        assertThat(inv.getSubtotalWithoutTax()).isEqualByComparingTo("1000.00");
        assertThat(inv.getTotalDiscount()).isEqualByComparingTo("100.00");
        assertThat(inv.getTotalTax()).isEqualByComparingTo("162.00");
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("1062.00");
    }

    @Test
    @DisplayName("Should clamp item discount if discount exceeds line amount")
    void shouldClampExcessiveItemDiscount() {
        Product p = createProduct(103L, "Budget Item", 50.00, 5.00);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        req.setCustomerId(1L);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(p.getId());
        item.setQty(1); // line amount 50
        item.setPricePerUnit(BigDecimal.valueOf(50.00));
        item.setDiscountValue(BigDecimal.valueOf(150.00)); // exceeds 50
        item.setGstPercent(BigDecimal.valueOf(5.00));
        req.setItems(List.of(item));

        Invoice inv = engine.calculate(new Invoice(), mockCustomer, mockProducts, req, false);

        assertThat(inv.getSubtotalWithoutTax()).isEqualByComparingTo("50.00");
        assertThat(inv.getTotalDiscount()).isEqualByComparingTo("50.00");
        assertThat(inv.getTotalTax()).isEqualByComparingTo("0.00");
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Should calculate invoice-level percentage discount and distribute across items")
    void shouldCalculateInvoiceLevelPercentageDiscount() {
        Product p1 = createProduct(104L, "Item A", 200.00, 18.00);
        Product p2 = createProduct(105L, "Item B", 300.00, 12.00);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        req.setCustomerId(1L);

        InvoiceRequestItem i1 = new InvoiceRequestItem();
        i1.setProductId(p1.getId());
        i1.setQty(1);
        i1.setPricePerUnit(BigDecimal.valueOf(200.00));
        i1.setGstPercent(BigDecimal.valueOf(18.00));

        InvoiceRequestItem i2 = new InvoiceRequestItem();
        i2.setProductId(p2.getId());
        i2.setQty(1);
        i2.setPricePerUnit(BigDecimal.valueOf(300.00));
        i2.setGstPercent(BigDecimal.valueOf(12.00));

        req.setItems(List.of(i1, i2));

        // 10% invoice discount on subtotal 500 = 50 discount (taxable = 450)
        InvoiceRequest.Discount disc = new InvoiceRequest.Discount();
        disc.setType("PERCENT");
        disc.setValue(BigDecimal.valueOf(10.0));
        req.setInvoiceDiscount(disc);

        Invoice inv = engine.calculate(new Invoice(), mockCustomer, mockProducts, req, false);

        assertThat(inv.getSubtotalWithoutTax()).isEqualByComparingTo("500.00");
        assertThat(inv.getTotalDiscount()).isEqualByComparingTo("50.00");
        assertThat(inv.getTotalTax()).isEqualByComparingTo("64.80");
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("514.80");
    }

    @Test
    @DisplayName("Should apply Round-Off correctly to nearest integer when enabled")
    void shouldApplyRoundOffWhenEnabled() {
        Product p = createProduct(106L, "Fractional Item", 33.33, 18.00);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        req.setCustomerId(1L);
        req.setRoundOff(true);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(p.getId());
        item.setQty(1);
        item.setPricePerUnit(BigDecimal.valueOf(33.33));
        item.setGstPercent(BigDecimal.valueOf(18.00)); // 33.33 * 0.18 = 5.9994 -> 6.00 GST -> 39.33 Total
        req.setItems(List.of(item));

        Invoice inv = engine.calculate(new Invoice(), mockCustomer, mockProducts, req, false);

        assertThat(inv.getRoundOff()).isNotNull();
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("39.00");
    }

    @Test
    @DisplayName("Should handle empty items list gracefully")
    void shouldHandleEmptyItems() {
        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        req.setCustomerId(1L);
        req.setItems(List.of());

        Invoice inv = engine.calculate(new Invoice(), mockCustomer, mockProducts, req, false);

        assertThat(inv.getSubtotalWithoutTax()).isEqualByComparingTo("0.00");
        assertThat(inv.getTotalDiscount()).isEqualByComparingTo("0.00");
        assertThat(inv.getTotalTax()).isEqualByComparingTo("0.00");
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Fixed invoice discount repeated recalculation must be idempotent and preserve base item discount")
    void fixedInvoiceDiscount_repeatedRecalculation_isIdempotent() {
        Product p1 = createProduct(201L, "Widget A", 100.00, 0.0);
        Product p2 = createProduct(202L, "Widget B", 100.00, 0.0);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        req.setCustomerId(1L);

        InvoiceRequestItem i1 = new InvoiceRequestItem();
        i1.setProductId(p1.getId());
        i1.setQty(1);
        i1.setPricePerUnit(BigDecimal.valueOf(100.00));
        i1.setDiscountValue(BigDecimal.ZERO);
        i1.setGstPercent(BigDecimal.ZERO);

        InvoiceRequestItem i2 = new InvoiceRequestItem();
        i2.setProductId(p2.getId());
        i2.setQty(1);
        i2.setPricePerUnit(BigDecimal.valueOf(100.00));
        i2.setDiscountValue(BigDecimal.ZERO);
        i2.setGstPercent(BigDecimal.ZERO);

        req.setItems(List.of(i1, i2));

        InvoiceRequest.Discount disc = new InvoiceRequest.Discount();
        disc.setType("VALUE");
        disc.setValue(BigDecimal.valueOf(20.00));
        req.setInvoiceDiscount(disc);

        Invoice inv1 = engine.calculate(new Invoice(), mockCustomer, mockProducts, req, false);

        assertThat(inv1.getSubtotalWithoutTax()).isEqualByComparingTo("200.00");
        assertThat(inv1.getTotalDiscount()).isEqualByComparingTo("20.00");
        assertThat(inv1.getTotalAmount()).isEqualByComparingTo("180.00");
        assertThat(inv1.getItems().get(0).getDiscountValue()).isEqualByComparingTo("0.00");
        assertThat(inv1.getItems().get(1).getDiscountValue()).isEqualByComparingTo("0.00");
        assertThat(inv1.getItems().get(0).getTaxableAmount()).isEqualByComparingTo("90.00");
        assertThat(inv1.getItems().get(1).getTaxableAmount()).isEqualByComparingTo("90.00");

        // Pass 2, 3, 4: Reconstruct request from calculated items (simulating reload/re-save)
        Invoice currentInv = inv1;
        for (int pass = 2; pass <= 4; pass++) {
            InvoiceRequest nextReq = new InvoiceRequest();
            nextReq.setFirmId(1L);
            nextReq.setCustomerId(1L);
            nextReq.setInvoiceDiscount(disc);

            List<InvoiceRequestItem> nextItems = new ArrayList<>();
            for (InvoiceItem it : currentInv.getItems()) {
                InvoiceRequestItem ri = new InvoiceRequestItem();
                ri.setProductId(it.getProduct().getId());
                ri.setQty(it.getQty());
                ri.setPricePerUnit(it.getPricePerUnit());
                ri.setDiscountValue(it.getDiscountValue());
                ri.setGstPercent(it.getGstPercent());
                nextItems.add(ri);
            }
            nextReq.setItems(nextItems);

            currentInv = engine.calculate(new Invoice(), mockCustomer, mockProducts, nextReq, false);

            assertThat(currentInv.getSubtotalWithoutTax())
                    .as("Pass " + pass + " subtotalWithoutTax")
                    .isEqualByComparingTo("200.00");
            assertThat(currentInv.getTotalDiscount())
                    .as("Pass " + pass + " totalDiscount")
                    .isEqualByComparingTo("20.00");
            assertThat(currentInv.getTotalAmount())
                    .as("Pass " + pass + " totalAmount")
                    .isEqualByComparingTo("180.00");
            assertThat(currentInv.getItems().get(0).getDiscountValue())
                    .as("Pass " + pass + " Item 1 discountValue")
                    .isEqualByComparingTo("0.00");
            assertThat(currentInv.getItems().get(1).getDiscountValue())
                    .as("Pass " + pass + " Item 2 discountValue")
                    .isEqualByComparingTo("0.00");
            assertThat(currentInv.getItems().get(0).getTaxableAmount())
                    .as("Pass " + pass + " Item 1 taxableAmount")
                    .isEqualByComparingTo("90.00");
            assertThat(currentInv.getItems().get(1).getTaxableAmount())
                    .as("Pass " + pass + " Item 2 taxableAmount")
                    .isEqualByComparingTo("90.00");
        }
    }

    @Test
    @DisplayName("Percentage invoice discount repeated recalculation must be idempotent")
    void percentInvoiceDiscount_repeatedRecalculation_isIdempotent() {
        Product p1 = createProduct(301L, "Service A", 100.00, 0.0);
        Product p2 = createProduct(302L, "Service B", 100.00, 0.0);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        req.setCustomerId(1L);

        InvoiceRequestItem i1 = new InvoiceRequestItem();
        i1.setProductId(p1.getId());
        i1.setQty(1);
        i1.setPricePerUnit(BigDecimal.valueOf(100.00));
        i1.setDiscountValue(BigDecimal.ZERO);
        i1.setGstPercent(BigDecimal.ZERO);

        InvoiceRequestItem i2 = new InvoiceRequestItem();
        i2.setProductId(p2.getId());
        i2.setQty(1);
        i2.setPricePerUnit(BigDecimal.valueOf(100.00));
        i2.setDiscountValue(BigDecimal.ZERO);
        i2.setGstPercent(BigDecimal.ZERO);

        req.setItems(List.of(i1, i2));

        InvoiceRequest.Discount disc = new InvoiceRequest.Discount();
        disc.setType("PERCENT");
        disc.setValue(BigDecimal.valueOf(10.0));
        req.setInvoiceDiscount(disc);

        Invoice inv1 = engine.calculate(new Invoice(), mockCustomer, mockProducts, req, false);

        assertThat(inv1.getSubtotalWithoutTax()).isEqualByComparingTo("200.00");
        assertThat(inv1.getTotalDiscount()).isEqualByComparingTo("20.00");
        assertThat(inv1.getTotalAmount()).isEqualByComparingTo("180.00");
        assertThat(inv1.getItems().get(0).getDiscountValue()).isEqualByComparingTo("0.00");
        assertThat(inv1.getItems().get(1).getDiscountValue()).isEqualByComparingTo("0.00");

        Invoice currentInv = inv1;
        for (int pass = 2; pass <= 4; pass++) {
            InvoiceRequest nextReq = new InvoiceRequest();
            nextReq.setFirmId(1L);
            nextReq.setCustomerId(1L);
            nextReq.setInvoiceDiscount(disc);

            List<InvoiceRequestItem> nextItems = new ArrayList<>();
            for (InvoiceItem it : currentInv.getItems()) {
                InvoiceRequestItem ri = new InvoiceRequestItem();
                ri.setProductId(it.getProduct().getId());
                ri.setQty(it.getQty());
                ri.setPricePerUnit(it.getPricePerUnit());
                ri.setDiscountValue(it.getDiscountValue());
                ri.setGstPercent(it.getGstPercent());
                nextItems.add(ri);
            }
            nextReq.setItems(nextItems);

            currentInv = engine.calculate(new Invoice(), mockCustomer, mockProducts, nextReq, false);

            assertThat(currentInv.getSubtotalWithoutTax())
                    .as("Pass " + pass + " subtotalWithoutTax")
                    .isEqualByComparingTo("200.00");
            assertThat(currentInv.getTotalDiscount())
                    .as("Pass " + pass + " totalDiscount")
                    .isEqualByComparingTo("20.00");
            assertThat(currentInv.getTotalAmount())
                    .as("Pass " + pass + " totalAmount")
                    .isEqualByComparingTo("180.00");
            assertThat(currentInv.getItems().get(0).getDiscountValue())
                    .as("Pass " + pass + " Item 1 discountValue")
                    .isEqualByComparingTo("0.00");
            assertThat(currentInv.getItems().get(1).getDiscountValue())
                    .as("Pass " + pass + " Item 2 discountValue")
                    .isEqualByComparingTo("0.00");
        }
    }

    @Test
    @DisplayName("Combined item-level and invoice-level discounts must remain logically separated across recalculations")
    void combinedItemAndInvoiceDiscount_preservesBaseItemDiscountSeparately() {
        Product p1 = createProduct(401L, "Product X", 100.00, 0.0);
        Product p2 = createProduct(402L, "Product Y", 100.00, 0.0);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        req.setCustomerId(1L);

        InvoiceRequestItem i1 = new InvoiceRequestItem();
        i1.setProductId(p1.getId());
        i1.setQty(1);
        i1.setPricePerUnit(BigDecimal.valueOf(100.00));
        i1.setDiscountValue(BigDecimal.valueOf(10.00));
        i1.setGstPercent(BigDecimal.ZERO);

        InvoiceRequestItem i2 = new InvoiceRequestItem();
        i2.setProductId(p2.getId());
        i2.setQty(1);
        i2.setPricePerUnit(BigDecimal.valueOf(100.00));
        i2.setDiscountValue(BigDecimal.valueOf(20.00));
        i2.setGstPercent(BigDecimal.ZERO);

        req.setItems(List.of(i1, i2));

        InvoiceRequest.Discount disc = new InvoiceRequest.Discount();
        disc.setType("VALUE");
        disc.setValue(BigDecimal.valueOf(10.00));
        req.setInvoiceDiscount(disc);

        Invoice inv1 = engine.calculate(new Invoice(), mockCustomer, mockProducts, req, false);

        assertThat(inv1.getSubtotalWithoutTax()).isEqualByComparingTo("200.00");
        assertThat(inv1.getTotalDiscount()).isEqualByComparingTo("40.00");
        assertThat(inv1.getTotalAmount()).isEqualByComparingTo("160.00");
        assertThat(inv1.getItems().get(0).getDiscountValue()).isEqualByComparingTo("10.00");
        assertThat(inv1.getItems().get(1).getDiscountValue()).isEqualByComparingTo("20.00");

        Invoice currentInv = inv1;
        for (int pass = 2; pass <= 4; pass++) {
            InvoiceRequest nextReq = new InvoiceRequest();
            nextReq.setFirmId(1L);
            nextReq.setCustomerId(1L);
            nextReq.setInvoiceDiscount(disc);

            List<InvoiceRequestItem> nextItems = new ArrayList<>();
            for (InvoiceItem it : currentInv.getItems()) {
                InvoiceRequestItem ri = new InvoiceRequestItem();
                ri.setProductId(it.getProduct().getId());
                ri.setQty(it.getQty());
                ri.setPricePerUnit(it.getPricePerUnit());
                ri.setDiscountValue(it.getDiscountValue());
                ri.setGstPercent(it.getGstPercent());
                nextItems.add(ri);
            }
            nextReq.setItems(nextItems);

            currentInv = engine.calculate(new Invoice(), mockCustomer, mockProducts, nextReq, false);

            assertThat(currentInv.getSubtotalWithoutTax())
                    .as("Pass " + pass + " subtotalWithoutTax")
                    .isEqualByComparingTo("200.00");
            assertThat(currentInv.getTotalDiscount())
                    .as("Pass " + pass + " totalDiscount")
                    .isEqualByComparingTo("40.00");
            assertThat(currentInv.getTotalAmount())
                    .as("Pass " + pass + " totalAmount")
                    .isEqualByComparingTo("160.00");
            assertThat(currentInv.getItems().get(0).getDiscountValue())
                    .as("Pass " + pass + " Item 1 discountValue")
                    .isEqualByComparingTo("10.00");
            assertThat(currentInv.getItems().get(1).getDiscountValue())
                    .as("Pass " + pass + " Item 2 discountValue")
                    .isEqualByComparingTo("20.00");
        }
    }

    @Test
    @DisplayName("Stage 2 Boundary: Subtotal 200 with fixed invoice discount 500 clamps to zero and does not produce negatives")
    void extremeInvoiceDiscount_greaterThanSubtotal_clampsTaxableAndPreservesItemDiscounts() {
        Product p1 = createProduct(501L, "Item 1", 100.00, 18.0);
        Product p2 = createProduct(502L, "Item 2", 100.00, 18.0);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        req.setCustomerId(1L);

        InvoiceRequestItem i1 = new InvoiceRequestItem();
        i1.setProductId(p1.getId());
        i1.setQty(1);
        i1.setPricePerUnit(BigDecimal.valueOf(100.00));
        i1.setDiscountValue(BigDecimal.valueOf(10.00)); // user-entered item discount
        i1.setGstPercent(BigDecimal.valueOf(18.0));

        InvoiceRequestItem i2 = new InvoiceRequestItem();
        i2.setProductId(p2.getId());
        i2.setQty(1);
        i2.setPricePerUnit(BigDecimal.valueOf(100.00));
        i2.setDiscountValue(BigDecimal.ZERO);
        i2.setGstPercent(BigDecimal.valueOf(18.0));

        req.setItems(List.of(i1, i2));

        InvoiceRequest.Discount disc = new InvoiceRequest.Discount();
        disc.setType("VALUE");
        disc.setValue(BigDecimal.valueOf(500.00)); // extreme discount > subtotal
        req.setInvoiceDiscount(disc);

        Invoice inv = engine.calculate(new Invoice(), mockCustomer, mockProducts, req, false);

        assertThat(inv.getSubtotalWithoutTax()).isEqualByComparingTo("200.00");
        // taxable amounts must not be negative
        assertThat(inv.getItems().get(0).getTaxableAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getItems().get(1).getTaxableAmount()).isEqualByComparingTo("0.00");
        // GST must not be negative
        assertThat(inv.getItems().get(0).getGstAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getItems().get(1).getGstAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getTotalTax()).isEqualByComparingTo("0.00");
        // Grand total must not be negative
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("0.00");
        // Item discounts MUST remain user-entered
        assertThat(inv.getItems().get(0).getDiscountValue()).isEqualByComparingTo("10.00");
        assertThat(inv.getItems().get(1).getDiscountValue()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Stage 2 Boundary: Subtotal exactly equal to invoice discount zeros out taxable, tax, and total")
    void invoiceSubtotal_exactlyEqualToInvoiceDiscount_zerosOutTaxableAndTotals() {
        Product p1 = createProduct(601L, "Item A", 100.00, 18.0);
        Product p2 = createProduct(602L, "Item B", 100.00, 18.0);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        req.setCustomerId(1L);

        InvoiceRequestItem i1 = new InvoiceRequestItem();
        i1.setProductId(p1.getId());
        i1.setQty(1);
        i1.setPricePerUnit(BigDecimal.valueOf(100.00));
        i1.setDiscountValue(BigDecimal.ZERO);
        i1.setGstPercent(BigDecimal.valueOf(18.0));

        InvoiceRequestItem i2 = new InvoiceRequestItem();
        i2.setProductId(p2.getId());
        i2.setQty(1);
        i2.setPricePerUnit(BigDecimal.valueOf(100.00));
        i2.setDiscountValue(BigDecimal.ZERO);
        i2.setGstPercent(BigDecimal.valueOf(18.0));

        req.setItems(List.of(i1, i2));

        InvoiceRequest.Discount disc = new InvoiceRequest.Discount();
        disc.setType("VALUE");
        disc.setValue(BigDecimal.valueOf(200.00)); // Exactly equal to subtotal
        req.setInvoiceDiscount(disc);

        Invoice inv = engine.calculate(new Invoice(), mockCustomer, mockProducts, req, false);

        assertThat(inv.getSubtotalWithoutTax()).isEqualByComparingTo("200.00");
        assertThat(inv.getItems().get(0).getTaxableAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getItems().get(1).getTaxableAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getItems().get(0).getGstAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getItems().get(1).getGstAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getTotalTax()).isEqualByComparingTo("0.00");
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getItems().get(0).getDiscountValue()).isEqualByComparingTo("0.00");
        assertThat(inv.getItems().get(1).getDiscountValue()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Stage 2 Boundary: Mixed zero-taxable and positive-taxable items avoids divide-by-zero and distributes deterministically")
    void multipleItems_someZeroTaxable_positiveTaxable_noDivideByZero() {
        Product p1 = createProduct(701L, "Zero Basis", 100.00, 0.0);
        Product p2 = createProduct(702L, "Full Basis", 100.00, 0.0);
        Product p3 = createProduct(703L, "Half Basis", 100.00, 0.0);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        req.setCustomerId(1L);

        // Item 1: 100 - 100 discount = 0 taxable
        InvoiceRequestItem i1 = new InvoiceRequestItem();
        i1.setProductId(p1.getId());
        i1.setQty(1);
        i1.setPricePerUnit(BigDecimal.valueOf(100.00));
        i1.setDiscountValue(BigDecimal.valueOf(100.00));
        i1.setGstPercent(BigDecimal.ZERO);

        // Item 2: 100 - 0 discount = 100 taxable
        InvoiceRequestItem i2 = new InvoiceRequestItem();
        i2.setProductId(p2.getId());
        i2.setQty(1);
        i2.setPricePerUnit(BigDecimal.valueOf(100.00));
        i2.setDiscountValue(BigDecimal.ZERO);
        i2.setGstPercent(BigDecimal.ZERO);

        // Item 3: 100 - 50 discount = 50 taxable
        InvoiceRequestItem i3 = new InvoiceRequestItem();
        i3.setProductId(p3.getId());
        i3.setQty(1);
        i3.setPricePerUnit(BigDecimal.valueOf(100.00));
        i3.setDiscountValue(BigDecimal.valueOf(50.00));
        i3.setGstPercent(BigDecimal.ZERO);

        req.setItems(List.of(i1, i2, i3));

        // Invoice discount = 30.00 on total taxable basis 150.00
        InvoiceRequest.Discount disc = new InvoiceRequest.Discount();
        disc.setType("VALUE");
        disc.setValue(BigDecimal.valueOf(30.00));
        req.setInvoiceDiscount(disc);

        Invoice inv = engine.calculate(new Invoice(), mockCustomer, mockProducts, req, false);

        // Item 1 had 0 taxable -> gets 0 share -> taxable remains 0
        assertThat(inv.getItems().get(0).getTaxableAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getItems().get(0).getDiscountValue()).isEqualByComparingTo("100.00");

        // Item 2 has 100/150 share = 20.00 -> taxable becomes 80.00
        assertThat(inv.getItems().get(1).getTaxableAmount()).isEqualByComparingTo("80.00");
        assertThat(inv.getItems().get(1).getDiscountValue()).isEqualByComparingTo("0.00");

        // Item 3 (last item) absorbs remaining: 30 - 20 = 10.00 -> taxable becomes 40.00
        assertThat(inv.getItems().get(2).getTaxableAmount()).isEqualByComparingTo("40.00");
        assertThat(inv.getItems().get(2).getDiscountValue()).isEqualByComparingTo("50.00");

        assertThat(inv.getTotalDiscount()).isEqualByComparingTo("180.00"); // 150 items + 30 inv
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    @DisplayName("Stage 2 Boundary: Extreme discount calculation repeated passes remain strictly idempotent")
    void repeatCalculation_extremeDiscount_isIdempotent() {
        Product p1 = createProduct(801L, "Extreme P1", 100.00, 0.0);
        Product p2 = createProduct(802L, "Extreme P2", 100.00, 0.0);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        req.setCustomerId(1L);

        InvoiceRequestItem i1 = new InvoiceRequestItem();
        i1.setProductId(p1.getId());
        i1.setQty(1);
        i1.setPricePerUnit(BigDecimal.valueOf(100.00));
        i1.setDiscountValue(BigDecimal.valueOf(15.00));
        i1.setGstPercent(BigDecimal.ZERO);

        InvoiceRequestItem i2 = new InvoiceRequestItem();
        i2.setProductId(p2.getId());
        i2.setQty(1);
        i2.setPricePerUnit(BigDecimal.valueOf(100.00));
        i2.setDiscountValue(BigDecimal.ZERO);
        i2.setGstPercent(BigDecimal.ZERO);

        req.setItems(List.of(i1, i2));

        InvoiceRequest.Discount disc = new InvoiceRequest.Discount();
        disc.setType("VALUE");
        disc.setValue(BigDecimal.valueOf(500.00));
        req.setInvoiceDiscount(disc);

        Invoice currentInv = engine.calculate(new Invoice(), mockCustomer, mockProducts, req, false);

        for (int pass = 1; pass <= 4; pass++) {
            assertThat(currentInv.getSubtotalWithoutTax()).isEqualByComparingTo("200.00");
            assertThat(currentInv.getTotalAmount()).isEqualByComparingTo("0.00");
            assertThat(currentInv.getItems().get(0).getDiscountValue()).isEqualByComparingTo("15.00");
            assertThat(currentInv.getItems().get(1).getDiscountValue()).isEqualByComparingTo("0.00");
            assertThat(currentInv.getItems().get(0).getTaxableAmount()).isEqualByComparingTo("0.00");
            assertThat(currentInv.getItems().get(1).getTaxableAmount()).isEqualByComparingTo("0.00");

            // Reconstruct next pass
            InvoiceRequest nextReq = new InvoiceRequest();
            nextReq.setFirmId(1L);
            nextReq.setCustomerId(1L);
            nextReq.setInvoiceDiscount(disc);
            List<InvoiceRequestItem> nextItems = new ArrayList<>();
            for (InvoiceItem it : currentInv.getItems()) {
                InvoiceRequestItem ri = new InvoiceRequestItem();
                ri.setProductId(it.getProduct().getId());
                ri.setQty(it.getQty());
                ri.setPricePerUnit(it.getPricePerUnit());
                ri.setDiscountValue(it.getDiscountValue());
                ri.setGstPercent(it.getGstPercent());
                nextItems.add(ri);
            }
            nextReq.setItems(nextItems);
            currentInv = engine.calculate(new Invoice(), mockCustomer, mockProducts, nextReq, false);
        }
    }

    @Test
    @DisplayName("Stage 2 Boundary: 100 percent invoice discount zeros out taxable basis without mutating item discounts")
    void percentInvoiceDiscount_boundary100Percent_zerosTaxableAmount() {
        Product p1 = createProduct(901L, "Percent P1", 100.00, 18.0);
        Product p2 = createProduct(902L, "Percent P2", 100.00, 18.0);

        InvoiceRequest req = new InvoiceRequest();
        req.setFirmId(1L);
        req.setCustomerId(1L);

        InvoiceRequestItem i1 = new InvoiceRequestItem();
        i1.setProductId(p1.getId());
        i1.setQty(1);
        i1.setPricePerUnit(BigDecimal.valueOf(100.00));
        i1.setDiscountValue(BigDecimal.valueOf(5.00));
        i1.setGstPercent(BigDecimal.valueOf(18.0));

        InvoiceRequestItem i2 = new InvoiceRequestItem();
        i2.setProductId(p2.getId());
        i2.setQty(1);
        i2.setPricePerUnit(BigDecimal.valueOf(100.00));
        i2.setDiscountValue(BigDecimal.ZERO);
        i2.setGstPercent(BigDecimal.valueOf(18.0));

        req.setItems(List.of(i1, i2));

        InvoiceRequest.Discount disc = new InvoiceRequest.Discount();
        disc.setType("PERCENT");
        disc.setValue(BigDecimal.valueOf(100.0)); // 100% discount
        req.setInvoiceDiscount(disc);

        Invoice inv = engine.calculate(new Invoice(), mockCustomer, mockProducts, req, false);

        assertThat(inv.getSubtotalWithoutTax()).isEqualByComparingTo("200.00");
        assertThat(inv.getItems().get(0).getTaxableAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getItems().get(1).getTaxableAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getTotalTax()).isEqualByComparingTo("0.00");
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getItems().get(0).getDiscountValue()).isEqualByComparingTo("5.00");
        assertThat(inv.getItems().get(1).getDiscountValue()).isEqualByComparingTo("0.00");
    }
}
