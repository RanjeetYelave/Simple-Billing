package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

/**
 * Line item in a Sales Return.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sales_return_items")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SalesReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_return_id", nullable = false)
    @JsonBackReference
    private SalesReturn salesReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_item_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "invoice"})
    private InvoiceItem invoiceItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @Column(nullable = false)
    private String productName;

    private String hsnCode;

    private String unit;

    @Column(nullable = false)
    private Integer returnQty;

    @Column(precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal discountValue;

    @Column(precision = 15, scale = 2)
    private BigDecimal gstPercent;

    @Column(precision = 15, scale = 2)
    private BigDecimal gstAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal refundTotal;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SalesReturn getSalesReturn() { return salesReturn; }
    public void setSalesReturn(SalesReturn salesReturn) { this.salesReturn = salesReturn; }

    public InvoiceItem getInvoiceItem() { return invoiceItem; }
    public void setInvoiceItem(InvoiceItem invoiceItem) { this.invoiceItem = invoiceItem; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Integer getReturnQty() { return returnQty; }
    public void setReturnQty(Integer returnQty) { this.returnQty = returnQty; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }

    public BigDecimal getGstPercent() { return gstPercent; }
    public void setGstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; }

    public BigDecimal getGstAmount() { return gstAmount; }
    public void setGstAmount(BigDecimal gstAmount) { this.gstAmount = gstAmount; }

    public BigDecimal getRefundTotal() { return refundTotal; }
    public void setRefundTotal(BigDecimal refundTotal) { this.refundTotal = refundTotal; }

    public static SalesReturnItemBuilder builder() {
        return new SalesReturnItemBuilder();
    }

    public static class SalesReturnItemBuilder {
        private Long id;
        private SalesReturn salesReturn;
        private InvoiceItem invoiceItem;
        private Product product;
        private String productName;
        private String hsnCode;
        private String unit;
        private Integer returnQty;
        private BigDecimal unitPrice;
        private BigDecimal discountValue;
        private BigDecimal gstPercent;
        private BigDecimal gstAmount;
        private BigDecimal refundTotal;

        public SalesReturnItemBuilder id(Long id) { this.id = id; return this; }
        public SalesReturnItemBuilder salesReturn(SalesReturn salesReturn) { this.salesReturn = salesReturn; return this; }
        public SalesReturnItemBuilder invoiceItem(InvoiceItem invoiceItem) { this.invoiceItem = invoiceItem; return this; }
        public SalesReturnItemBuilder product(Product product) { this.product = product; return this; }
        public SalesReturnItemBuilder productName(String productName) { this.productName = productName; return this; }
        public SalesReturnItemBuilder hsnCode(String hsnCode) { this.hsnCode = hsnCode; return this; }
        public SalesReturnItemBuilder unit(String unit) { this.unit = unit; return this; }
        public SalesReturnItemBuilder returnQty(Integer returnQty) { this.returnQty = returnQty; return this; }
        public SalesReturnItemBuilder unitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; return this; }
        public SalesReturnItemBuilder discountValue(BigDecimal discountValue) { this.discountValue = discountValue; return this; }
        public SalesReturnItemBuilder gstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; return this; }
        public SalesReturnItemBuilder gstAmount(BigDecimal gstAmount) { this.gstAmount = gstAmount; return this; }
        public SalesReturnItemBuilder refundTotal(BigDecimal refundTotal) { this.refundTotal = refundTotal; return this; }

        public SalesReturnItem build() {
            SalesReturnItem item = new SalesReturnItem();
            item.id = this.id;
            item.salesReturn = this.salesReturn;
            item.invoiceItem = this.invoiceItem;
            item.product = this.product;
            item.productName = this.productName;
            item.hsnCode = this.hsnCode;
            item.unit = this.unit;
            item.returnQty = this.returnQty;
            item.unitPrice = this.unitPrice;
            item.discountValue = this.discountValue;
            item.gstPercent = this.gstPercent;
            item.gstAmount = this.gstAmount;
            item.refundTotal = this.refundTotal;
            return item;
        }
    }
}


