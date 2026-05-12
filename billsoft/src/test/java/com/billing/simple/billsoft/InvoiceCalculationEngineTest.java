package com.billing.simple.billsoft;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequest.Discount;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.engine.InvoiceCalculationEngine;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.entities.Product;

class InvoiceCalculationEngineTest {

    private final InvoiceCalculationEngine engine = new InvoiceCalculationEngine();

    // --------------------------------------------------------------------
    // 1. Simple: single item, no discount, no GST
    // --------------------------------------------------------------------
    @Test
    void singleItem_noDiscount_noGst_shouldComputeSimpleTotals() {
        InvoiceRequest req = new InvoiceRequest();
        req.setStatus(InvoiceStatus.FINAL);
        req.setPaid(false);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(1L);
        item.setQty(2);
        item.setPricePerUnit(new BigDecimal("100.00"));
        item.setGstPercent(BigDecimal.ZERO);
        req.setItems(List.of(item));

        Product p = new Product();
        p.setId(1L);
        p.setPrice(new BigDecimal("100.00"));
        p.setGstPercentage(BigDecimal.ZERO);
        p.setUnit("pcs");

        Invoice invoice = new Invoice();

        Invoice result = engine.calculate(invoice, null, List.of(p), req, false);

        assertThat(result.getSubtotalWithoutTax()).isEqualByComparingTo("200.00");
        assertThat(result.getTotalTax()).isEqualByComparingTo("0.00");
        assertThat(result.getTotalDiscount()).isEqualByComparingTo("0.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("200.00");
        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.FINAL);
        assertThat(result.getDueDate()).isEqualTo(LocalDate.now().plusDays(14));
        assertThat(result.getInvoiceDate()).isNotNull();

        assertThat(result.getItems()).hasSize(1);
        InvoiceItem line = result.getItems().get(0);
        assertThat(line.getQty()).isEqualTo(2);
        assertThat(line.getAmountWithoutTax()).isEqualByComparingTo("200.00");
        assertThat(line.getTaxableAmount()).isEqualByComparingTo("200.00");
        assertThat(line.getLineTotal()).isEqualByComparingTo("200.00");
    }

    // --------------------------------------------------------------------
    // 2. Item-level VALUE discount + GST
    // --------------------------------------------------------------------
    @Test
    void singleItem_valueDiscount_withGst_shouldApplyDiscountThenGst() {
        InvoiceRequest req = new InvoiceRequest();
        req.setStatus(InvoiceStatus.FINAL);
        req.setPaid(false);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(1L);
        item.setQty(2);
        item.setPricePerUnit(new BigDecimal("100.00"));
        item.setDiscountValue(new BigDecimal("20.00")); // flat discount
        item.setGstPercent(new BigDecimal("18"));
        req.setItems(List.of(item));

        Product p = new Product();
        p.setId(1L);
        p.setPrice(new BigDecimal("100.00"));
        p.setGstPercentage(new BigDecimal("18"));
        p.setUnit("pcs");

        Invoice invoice = new Invoice();

        Invoice result = engine.calculate(invoice, null, List.of(p), req, false);

        // Raw: 200 - 20 = 180 taxable → GST = 32.40 → Total = 212.40
        assertThat(result.getSubtotalWithoutTax()).isEqualByComparingTo("200.00");
        assertThat(result.getTotalDiscount()).isEqualByComparingTo("20.00");
        assertThat(result.getTotalTax()).isEqualByComparingTo("32.40");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("212.40");

        InvoiceItem line = result.getItems().get(0);
        assertThat(line.getDiscountType()).isEqualTo("VALUE");
        assertThat(line.getTaxableAmount()).isEqualByComparingTo("180.00");
    }

    // --------------------------------------------------------------------
    // 3. Invoice-level PERCENT discount distributed across items
    // --------------------------------------------------------------------
    @Test
    void invoiceLevelPercentDiscount_shouldDistributeAcrossItems() {
        InvoiceRequest req = new InvoiceRequest();
        req.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem item1 = new InvoiceRequestItem();
        item1.setProductId(1L);
        item1.setQty(2);
        item1.setPricePerUnit(new BigDecimal("100.00"));
        item1.setGstPercent(new BigDecimal("18"));

        InvoiceRequestItem item2 = new InvoiceRequestItem();
        item2.setProductId(2L);
        item2.setQty(1);
        item2.setPricePerUnit(new BigDecimal("300.00"));
        item2.setGstPercent(new BigDecimal("18"));

        req.setItems(List.of(item1, item2));

        Discount disc = new Discount();
        disc.setType("PERCENT");
        disc.setValue(new BigDecimal("10")); // 10% invoice discount
        req.setInvoiceDiscount(disc);

        Product p1 = new Product();
        p1.setId(1L);
        p1.setPrice(new BigDecimal("100.00"));
        p1.setGstPercentage(new BigDecimal("18"));

        Product p2 = new Product();
        p2.setId(2L);
        p2.setPrice(new BigDecimal("300.00"));
        p2.setGstPercentage(new BigDecimal("18"));

        Invoice invoice = new Invoice();

        Invoice result = engine.calculate(invoice, null, List.of(p1, p2), req, false);

        assertThat(result.getSubtotalWithoutTax()).isEqualByComparingTo("500.00");
        assertThat(result.getTotalDiscount()).isEqualByComparingTo("50.00");
        assertThat(result.getTotalTax()).isEqualByComparingTo("81.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("531.00");
        assertThat(result.getInvoiceDiscountValue()).isEqualByComparingTo("10");
    }

    // --------------------------------------------------------------------
    // 4. Invoice-level VALUE discount + ROUND OFF
    // --------------------------------------------------------------------
    @Test
    void invoiceLevelValueDiscount_withRoundOff_shouldApplyRoundOffProperly() {
        InvoiceRequest req = new InvoiceRequest();
        req.setStatus(InvoiceStatus.FINAL);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(5L);
        item.setQty(1);
        item.setPricePerUnit(new BigDecimal("123.45"));
        item.setGstPercent(new BigDecimal("18"));
        req.setItems(List.of(item));

        Discount disc = new Discount();
        disc.setType("VALUE");
        disc.setValue(new BigDecimal("10.45"));
        req.setInvoiceDiscount(disc);

        req.setRoundOff(BigDecimal.ONE); // enable rounding

        Product p = new Product();
        p.setId(5L);
        p.setPrice(new BigDecimal("123.45"));
        p.setGstPercentage(new BigDecimal("18"));

        Invoice invoice = new Invoice();

        Invoice result = engine.calculate(invoice, null, List.of(p), req, false);

        // Final amount must be an integer
        assertThat(result.getTotalAmount().scale()).isEqualTo(0);
        assertThat(result.getRoundOff()).isNotNull();
    }

    // --------------------------------------------------------------------
    // 5. ESTIMATE should not auto due date
    // --------------------------------------------------------------------
    @Test
    void estimateStatus_shouldNotForceDueDate() {
        InvoiceRequest req = new InvoiceRequest();
        req.setStatus(InvoiceStatus.ESTIMATE);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(1L);
        item.setQty(1);
        item.setPricePerUnit(new BigDecimal("50.00"));
        req.setItems(List.of(item));

        Product p = new Product();
        p.setId(1L);
        p.setPrice(new BigDecimal("50.00"));

        Invoice invoice = new Invoice();

        Invoice result = engine.calculate(invoice, null, List.of(p), req, false);

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.ESTIMATE);
        assertThat(result.getDueDate()).isNull();
    }

    // --------------------------------------------------------------------
    // 6. Update mode: items null → retain existing
    // --------------------------------------------------------------------
    @Test
    void updateMode_shouldKeepExistingItemsWhenNull() {
        Invoice existing = new Invoice();
        existing.setStatus(InvoiceStatus.FINAL);

        InvoiceItem old = new InvoiceItem();
        old.setInvoice(existing);
        old.setQty(2);
        old.setPricePerUnit(new BigDecimal("100.00"));
        old.setTaxableAmount(new BigDecimal("200.00"));
        old.setGstAmount(new BigDecimal("36.00"));
        old.setLineTotal(new BigDecimal("236.00"));
        existing.getItems().add(old);

        existing.setSubtotalWithoutTax(new BigDecimal("200.00"));
        existing.setTotalTax(new BigDecimal("36.00"));
        existing.setTotalAmount(new BigDecimal("236.00"));

        InvoiceRequest req = new InvoiceRequest();
        req.setStatus(InvoiceStatus.FINAL);
        req.setItems(null);

        Invoice result = engine.calculate(existing, null, List.of(), req, true);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("236.00");
        assertThat(result.getTotalTax()).isEqualByComparingTo("36.00");
    }

    // --------------------------------------------------------------------
    // 7. Metadata copy check
    // --------------------------------------------------------------------
    @Test
    void metadata_shouldBeCopiedCorrectly() {
        Customer cust = new Customer();
        cust.setId(99L);
        cust.setName("Test");

        InvoiceRequest req = new InvoiceRequest();
        req.setCustomerNote("Note");
        req.setPaymentMethod("CASH");
        req.setTags("t1,t2");
        req.setCurrency("INR");

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(1L);
        item.setQty(1);
        item.setPricePerUnit(new BigDecimal("10.00"));
        req.setItems(List.of(item));

        Product p = new Product();
        p.setId(1L);
        p.setPrice(new BigDecimal("10.00"));

        Invoice invoice = new Invoice();

        Invoice result = engine.calculate(invoice, cust, List.of(p), req, false);

        assertThat(result.getCustomer()).isEqualTo(cust);
        assertThat(result.getPaymentMethod()).isEqualTo("CASH");
        assertThat(result.getTags()).isEqualTo("t1,t2");
    }
}
