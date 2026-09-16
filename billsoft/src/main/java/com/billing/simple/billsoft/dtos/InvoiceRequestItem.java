package com.billing.simple.billsoft.dtos;

import lombok.*;
import java.math.BigDecimal;

/**
 * Invoice line item request model.
 * UI must send productId + qty. Other values are optional.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRequestItem {

    /**
     * Must be provided by UI when selecting a product.
     * For custom/manual items, UI must create a temporary Product first.
     */
    private Long productId;
    private String productName;

    /* -------------------------------------------------------------
       QUANTITY & UNIT
    ------------------------------------------------------------- */
    private Integer qty;
    private String unit; // pcs, kg, box, etc.
    private String hsnCode; // HSN or SAC code

    /* -------------------------------------------------------------
       PRICING
       If null → backend falls back to Product.price
    ------------------------------------------------------------- */
    private BigDecimal pricePerUnit;

    /* -------------------------------------------------------------
       DISCOUNT (ITEM-LEVEL)
       VALUE ONLY (₹ discount amount per line)
    ------------------------------------------------------------- */
    private BigDecimal discountValue;   // ₹ discount amount

    /* -------------------------------------------------------------
       GST
       If null → backend uses Product.gstPercentage
    ------------------------------------------------------------- */
    private BigDecimal gstPercent;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }

    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(BigDecimal pricePerUnit) { this.pricePerUnit = pricePerUnit; }

    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }

    public BigDecimal getGstPercent() { return gstPercent; }
    public void setGstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; }

    public static InvoiceRequestItemBuilder builder() {
        return new InvoiceRequestItemBuilder();
    }

    public static class InvoiceRequestItemBuilder {
        private Long productId;
        private String productName;
        private Integer qty;
        private String unit;
        private String hsnCode;
        private BigDecimal pricePerUnit;
        private BigDecimal discountValue;
        private BigDecimal gstPercent;

        public InvoiceRequestItemBuilder productId(Long productId) { this.productId = productId; return this; }
        public InvoiceRequestItemBuilder productName(String productName) { this.productName = productName; return this; }
        public InvoiceRequestItemBuilder qty(Integer qty) { this.qty = qty; return this; }
        public InvoiceRequestItemBuilder unit(String unit) { this.unit = unit; return this; }
        public InvoiceRequestItemBuilder hsnCode(String hsnCode) { this.hsnCode = hsnCode; return this; }
        public InvoiceRequestItemBuilder pricePerUnit(BigDecimal pricePerUnit) { this.pricePerUnit = pricePerUnit; return this; }
        public InvoiceRequestItemBuilder discountValue(BigDecimal discountValue) { this.discountValue = discountValue; return this; }
        public InvoiceRequestItemBuilder gstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; return this; }

        public InvoiceRequestItem build() {
            InvoiceRequestItem item = new InvoiceRequestItem();
            item.productId = this.productId;
            item.productName = this.productName;
            item.qty = this.qty;
            item.unit = this.unit;
            item.hsnCode = this.hsnCode;
            item.pricePerUnit = this.pricePerUnit;
            item.discountValue = this.discountValue;
            item.gstPercent = this.gstPercent;
            return item;
        }
    }
}

