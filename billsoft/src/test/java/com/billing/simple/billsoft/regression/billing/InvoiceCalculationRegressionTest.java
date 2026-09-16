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
}
