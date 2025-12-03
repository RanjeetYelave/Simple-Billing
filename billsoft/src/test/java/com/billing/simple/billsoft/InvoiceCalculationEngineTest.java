package com.billing.simple.billsoft;

import com.billing.simple.billsoft.dtos.InvoiceRequest;
import com.billing.simple.billsoft.dtos.InvoiceRequest.Discount;
import com.billing.simple.billsoft.dtos.InvoiceRequestItem;
import com.billing.simple.billsoft.engine.InvoiceCalculationEngine;
import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceItem;
import com.billing.simple.billsoft.entities.InvoiceStatus;
import com.billing.simple.billsoft.entities.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceCalculationEngineTest {

    private final InvoiceCalculationEngine engine = new InvoiceCalculationEngine();

    @Test
    void singleItem_noDiscount_noGst_shouldComputeSimpleTotals() {
        // given
        InvoiceRequest req = new InvoiceRequest();
        req.setStatus(InvoiceStatus.FINAL);
        req.setPaid(false);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(1L);
        item.setQty(2); // 2 units
        item.setPricePerUnit(new BigDecimal("100.00"));
        item.setGstPercent(BigDecimal.ZERO);
        req.setItems(List.of(item));

        Product p = new Product();
        p.setId(1L);
        p.setPrice(new BigDecimal("100.00"));
        p.setGstPercentage(BigDecimal.ZERO);

        Invoice invoice = new Invoice();

        // when
        Invoice result = engine.calculate(
                invoice,
                (Customer) null,
                List.of(p),
                req,
                false
        );

        // then
        assertThat(result.getSubtotalWithoutTax()).isEqualByComparingTo("200.00");
        assertThat(result.getTotalTax()).isEqualByComparingTo("0.00");
        assertThat(result.getTotalDiscount()).isEqualByComparingTo("0.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("200.00");

        assertThat(result.getItems()).hasSize(1);
        InvoiceItem line = result.getItems().get(0);
        assertThat(line.getAmountWithoutTax()).isEqualByComparingTo("200.00");
        assertThat(line.getTaxableAmount()).isEqualByComparingTo("200.00");
        assertThat(line.getGstAmount()).isEqualByComparingTo("0.00");
        assertThat(line.getLineTotal()).isEqualByComparingTo("200.00");
    }

    @Test
    void singleItem_percentDiscount_withGst_shouldApplyDiscountThenGst() {
        // given
        InvoiceRequest req = new InvoiceRequest();
        req.setStatus(InvoiceStatus.FINAL);
        req.setPaid(false);

        InvoiceRequestItem item = new InvoiceRequestItem();
        item.setProductId(1L);
        item.setQty(2); // 2 x 100 = 200
        item.setPricePerUnit(new BigDecimal("100.00"));
        item.setDiscountPercent(new BigDecimal("10")); // 10% of 200 = 20
        item.setGstPercent(new BigDecimal("18"));
        req.setItems(List.of(item));

        Product p = new Product();
        p.setId(1L);
        p.setPrice(new BigDecimal("100.00"));
        p.setGstPercentage(new BigDecimal("18"));

        Invoice invoice = new Invoice();

        // when
        Invoice result = engine.calculate(
                invoice,
                null,
                List.of(p),
                req,
                false
        );

        // expected:
        // amountWithoutTax = 200
        // item discount = 20
        // taxable = 180
        // GST 18% of 180 = 32.40
        // total = 212.40

        assertThat(result.getSubtotalWithoutTax()).isEqualByComparingTo("180.00");
        assertThat(result.getTotalDiscount()).isEqualByComparingTo("20.00");
        assertThat(result.getTotalTax()).isEqualByComparingTo("32.40");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("212.40");

        InvoiceItem line = result.getItems().get(0);
        assertThat(line.getAmountWithoutTax()).isEqualByComparingTo("200.00");
        assertThat(line.getTaxableAmount()).isEqualByComparingTo("180.00");
        assertThat(line.getGstAmount()).isEqualByComparingTo("32.40");
        assertThat(line.getLineTotal()).isEqualByComparingTo("212.40");
    }

    @Test
    void invoiceLevelPercentDiscount_shouldDistributeAcrossItems() {
        // given
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
        disc.setValue(new BigDecimal("10")); // 10% invoice level on taxable subtotal
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

        // when
        Invoice result = engine.calculate(
                invoice,
                null,
                List.of(p1, p2),
                req,
                false
        );

        // Raw amounts: 200 + 300 = 500
        // 10% invoice discount on 500 -> 50
        // (per-item discount none, so all from invoice-level)
        // Taxable after all discounts = 450
        // GST 18% of 450 = 81
        // grand total = 531

        assertThat(result.getSubtotalWithoutTax()).isEqualByComparingTo("450.00");
        assertThat(result.getTotalDiscount()).isEqualByComparingTo("50.00");
        assertThat(result.getTotalTax()).isEqualByComparingTo("81.00");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("531.00");

        assertThat(result.getItems()).hasSize(2);
    }
}
