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
        assertThat(line.getUnit()).isEqualTo("pcs");
        assertThat(line.getAmountWithoutTax()).isEqualByComparingTo("200.00");
        assertThat(line.getTaxableAmount()).isEqualByComparingTo("200.00");
        assertThat(line.getGstAmount()).isEqualByComparingTo("0.00");
        assertThat(line.getLineTotal()).isEqualByComparingTo("200.00");
    }

    // --------------------------------------------------------------------
    // 2. Item-level VALUE discount + GST (no percent at item level anymore)
    // --------------------------------------------------------------------
    @Test
    void singleItem_itemLevelDiscount_withGst_shouldApplyDiscountThenGst() {
        InvoiceRequest req = new InvoiceRequest();
        req.setStatus(InvoiceStatus.FINAL);
        req.setPaid(false);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(1L);
        item.setQty(2); // 2 x 100 = 200
        item.setPricePerUnit(new BigDecimal("100.00"));
        item.setDiscountValue(new BigDecimal("20.00")); // flat 20 discount on line
        item.setGstPercent(new BigDecimal("18"));
        req.setItems(List.of(item));

        Product p = new Product();
        p.setId(1L);
        p.setPrice(new BigDecimal("100.00"));
        p.setGstPercentage(new BigDecimal("18"));
        p.setUnit("pcs");

        Invoice invoice = new Invoice();

        Invoice result = engine.calculate(invoice, null, List.of(p), req, false);

        // amountWithoutTax = 200
        // item discount (value) = 20
        // taxable = 180
        // GST 18% of 180 = 32.40
        // total = 212.40

        // subtotalWithoutTax is RAW sum before any discounts
        assertThat(result.getSubtotalWithoutTax()).isEqualByComparingTo("200.00");
        assertThat(result.getTotalDiscount()).isEqualByComparingTo("20.00");
        assertThat(result.getTotalTax()).isEqualByComparingTo("32.40");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("212.40");

        InvoiceItem line = result.getItems().get(0);
        assertThat(line.getAmountWithoutTax()).isEqualByComparingTo("200.00");
        assertThat(line.getTaxableAmount()).isEqualByComparingTo("180.00");
        assertThat(line.getGstAmount()).isEqualByComparingTo("32.40");
        assertThat(line.getLineTotal()).isEqualByComparingTo("212.40");
        assertThat(line.getDiscountValue()).isEqualByComparingTo("20.00");
    }

    // --------------------------------------------------------------------
    // 3. Item-level VALUE discount + GST from product
    // --------------------------------------------------------------------
    @Test
    void singleItem_valueDiscount_usesProductGstWhenNull() {
        InvoiceRequest req = new InvoiceRequest();
        req.setStatus(InvoiceStatus.FINAL);
        req.setPaid(true);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(10L);
        item.setQty(3); // 3 x 100 = 300
        item.setPricePerUnit(new BigDecimal("100.00"));
        item.setDiscountValue(new BigDecimal("50.00")); // flat 50
        // gstPercent is null -> should take from Product
        req.setItems(List.of(item));

        Product p = new Product();
        p.setId(10L);
        p.setPrice(new BigDecimal("100.00"));
        p.setGstPercentage(new BigDecimal("5")); // 5%
        p.setUnit("kg");

        Invoice invoice = new Invoice();

        Invoice result = engine.calculate(invoice, null, List.of(p), req, false);

        // amountWithoutTax = 300
        // discount = 50 => taxable = 250
        // GST 5% of 250 = 12.50
        // total = 262.50

        // subtotalWithoutTax = 300 (raw)
        assertThat(result.getSubtotalWithoutTax()).isEqualByComparingTo("300.00");
        assertThat(result.getTotalDiscount()).isEqualByComparingTo("50.00");
        assertThat(result.getTotalTax()).isEqualByComparingTo("12.50");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("262.50");
        assertThat(result.getPaid()).isTrue();

        InvoiceItem line = result.getItems().get(0);
        assertThat(line.getUnit()).isEqualTo("kg");
        assertThat(line.getDiscountValue()).isEqualByComparingTo("50.00");
        assertThat(line.getGstPercent()).isEqualByComparingTo("5.00");
    }

    // --------------------------------------------------------------------
    // 4. Invoice-level PERCENT discount distributed across multiple items
    // --------------------------------------------------------------------
    @Test
    void invoiceLevelPercentDiscount_shouldDistributeAcrossItems() {
        InvoiceRequest req = new InvoiceRequest();
        req.setStatus(InvoiceStatus.FINAL);
        req.setPaid(false);

        // Item1: 2 x 100 = 200
        InvoiceRequestItem item1 = new InvoiceRequestItem();
        item1.setProductId(1L);
        item1.setQty(2);
        item1.setPricePerUnit(new BigDecimal("100.00"));
        item1.setGstPercent(new BigDecimal("18"));

        // Item2: 1 x 300 = 300
        InvoiceRequestItem item2 = new InvoiceRequestItem();
        item2.setProductId(2L);
        item2.setQty(1);
        item2.setPricePerUnit(new BigDecimal("300.00"));
        item2.setGstPercent(new BigDecimal("18"));

        req.setItems(List.of(item1, item2));

        Discount disc = new Discount();
        disc.setType("PERCENT");
        disc.setValue(new BigDecimal("10")); // 10% of 500 = 50
        req.setInvoiceDiscount(disc);

        Product p1 = new Product();
        p1.setId(1L);
        p1.setPrice(new BigDecimal("100.00"));
        p1.setGstPercentage(new BigDecimal("18"));
        p1.setUnit("pcs");

        Product p2 = new Product();
        p2.setId(2L);
        p2.setPrice(new BigDecimal("300.00"));
        p2.setGstPercentage(new BigDecimal("18"));
        p2.setUnit("box");

        Invoice invoice = new Invoice();

        Invoice result = engine.calculate(invoice, null, List.of(p1, p2), req, false);

        // Raw amounts: 200 + 300 = 500
        // Invoice-level discount 10% -> 50
        // Taxable after all discounts: 450
        // GST 18% of 450 = 81
        // Total = 531

        // subtotalWithoutTax = 500 (raw, before any discounts)
        assertThat(result.getSubtotalWithoutTax()).isEqualByComparingTo("500.00");
        assertThat(result.getTotalDiscount()).isEqualByComparingTo("50.00");
        assertThat(result.getTotalTax()).isEqualByComparingTo("81.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("531.00");
        assertThat(result.getInvoiceDiscountType()).isEqualTo("PERCENT");
        assertThat(result.getInvoiceDiscountValue()).isEqualByComparingTo("10");

        assertThat(result.getItems()).hasSize(2);
    }

    // --------------------------------------------------------------------
    // 5. Invoice-level VALUE discount + round-off
    // --------------------------------------------------------------------
    @Test
    void invoiceLevelValueDiscount_withRoundOff_shouldSetRoundOffAndTotals() {
        InvoiceRequest req = new InvoiceRequest();
        req.setStatus(InvoiceStatus.FINAL);
        req.setPaid(false);

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

        // enabling roundOff flag – the actual value is ignored, only presence matters
        req.setRoundOff(BigDecimal.ONE);

        Product p = new Product();
        p.setId(5L);
        p.setPrice(new BigDecimal("123.45"));
        p.setGstPercentage(new BigDecimal("18"));
        p.setUnit("pcs");

        Invoice invoice = new Invoice();

        Invoice result = engine.calculate(invoice, null, List.of(p), req, false);

        // We don't care about the exact pennies after rounding,
        // just that:
        //  - roundOff is set
        //  - totalAmount is a whole number
        assertThat(result.getRoundOff()).isNotNull();
        assertThat(result.getRoundOff().scale()).isEqualTo(2);

        BigDecimal total = result.getTotalAmount();
        assertThat(total.scale()).isEqualTo(0); // integer amount after rounding
    }

    // --------------------------------------------------------------------
    // 6. ESTIMATE behaviour: no auto due date for non FINAL/DRAFT
    // --------------------------------------------------------------------
    @Test
    void estimateStatus_shouldNotForceDueDateWhenNotProvided() {
        InvoiceRequest req = new InvoiceRequest();
        req.setStatus(InvoiceStatus.ESTIMATE);
        req.setPaid(false);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(1L);
        item.setQty(1);
        item.setPricePerUnit(new BigDecimal("50.00"));
        req.setItems(List.of(item));

        Product p = new Product();
        p.setId(1L);
        p.setPrice(new BigDecimal("50.00"));
        p.setGstPercentage(BigDecimal.ZERO);

        Invoice invoice = new Invoice();

        Invoice result = engine.calculate(invoice, null, List.of(p), req, false);

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.ESTIMATE);
        // engine only auto-sets dueDate for FINAL/DRAFT
        assertThat(result.getDueDate()).isNull();
    }

    // --------------------------------------------------------------------
    // 7. Update-mode safety: request.items null -> retain existing DB items
    // --------------------------------------------------------------------
    @Test
    void updateMode_withNullItems_shouldKeepExistingItemsAndTotals() {
        // existing invoice with one line + totals
        Invoice existing = new Invoice();
        existing.setStatus(InvoiceStatus.FINAL);

        InvoiceItem oldItem = new InvoiceItem();
        oldItem.setQty(2);
        oldItem.setUnit("pcs");
        oldItem.setPricePerUnit(new BigDecimal("100.00"));
        oldItem.setAmountWithoutTax(new BigDecimal("200.00"));
        oldItem.setDiscountType("VALUE");
        oldItem.setDiscountValue(new BigDecimal("20.00"));
        oldItem.setTaxableAmount(new BigDecimal("180.00"));
        oldItem.setGstPercent(new BigDecimal("18.00"));
        oldItem.setGstAmount(new BigDecimal("32.40"));
        oldItem.setLineTotal(new BigDecimal("212.40"));
        oldItem.setInvoice(existing);

        existing.getItems().add(oldItem);

        existing.setSubtotalWithoutTax(new BigDecimal("180.00"));
        existing.setTotalTax(new BigDecimal("32.40"));
        existing.setTotalDiscount(new BigDecimal("20.00"));
        existing.setTotalAmount(new BigDecimal("212.40"));

        // request with null items -> should not recalc, should keep existing lines
        InvoiceRequest req = new InvoiceRequest();
        req.setStatus(InvoiceStatus.FINAL);
        req.setPaid(true);
        req.setItems(null);  // <<< key part

        Invoice result = engine.calculate(existing, null, List.of(), req, true);

        // Items remain 1 and still the same lineTotal
        assertThat(result.getItems()).hasSize(1);
        InvoiceItem line = result.getItems().get(0);
        assertThat(line.getLineTotal()).isEqualByComparingTo("212.40");

        // Totals unchanged
        assertThat(result.getSubtotalWithoutTax()).isEqualByComparingTo("180.00");
        assertThat(result.getTotalTax()).isEqualByComparingTo("32.40");
        assertThat(result.getTotalDiscount()).isEqualByComparingTo("20.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("212.40");
    }

    // --------------------------------------------------------------------
    // 8. Metadata: customer + notes + tags copied correctly
    // --------------------------------------------------------------------
    @Test
    void metadata_shouldBeCopiedFromRequestAndCustomerSet() {
        Customer cust = new Customer();
        cust.setId(99L);
        cust.setName("Test Customer");

        InvoiceRequest req = new InvoiceRequest();
        req.setStatus(null); // should default to FINAL
        req.setCustomerNote("Hello");
        req.setTermsAndConditions("TC");
        req.setPaymentMethod("CASH");
        req.setCurrency("INR");
        req.setTags("tag1,tag2");

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(1L);
        item.setQty(1);
        item.setPricePerUnit(new BigDecimal("10.00"));
        req.setItems(List.of(item));

        Product p = new Product();
        p.setId(1L);
        p.setPrice(new BigDecimal("10.00"));
        p.setGstPercentage(BigDecimal.ZERO);

        Invoice invoice = new Invoice();

        Invoice result = engine.calculate(invoice, cust, List.of(p), req, false);

        assertThat(result.getCustomer()).isEqualTo(cust);
        assertThat(result.getCustomerNote()).isEqualTo("Hello");
        assertThat(result.getTermsAndConditions()).isEqualTo("TC");
        assertThat(result.getPaymentMethod()).isEqualTo("CASH");
        assertThat(result.getCurrency()).isEqualTo("INR");
        assertThat(result.getTags()).isEqualTo("tag1,tag2");
        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.FINAL); // defaulted
    }
}
