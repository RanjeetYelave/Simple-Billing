package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Individual line item in a Purchase Order.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "purchase_order_items")
public class PurchaseOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    @JsonBackReference
    private PurchaseOrder purchaseOrder;

    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(length = 1000)
    private String description;

    @Column(length = 20)
    private String hsnCode;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Builder.Default
    @Column(length = 20)
    private String unit = "pcs";

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Builder.Default
    @Column(precision = 15, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Builder.Default
    @Column(precision = 15, scale = 2)
    private BigDecimal gstPercent = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal taxableAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal gstAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalAmount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public void setPurchaseOrder(PurchaseOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }

    public BigDecimal getGstPercent() { return gstPercent; }
    public void setGstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; }

    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount; }

    public BigDecimal getGstAmount() { return gstAmount; }
    public void setGstAmount(BigDecimal gstAmount) { this.gstAmount = gstAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public static PurchaseOrderItemBuilder builder() {
        return new PurchaseOrderItemBuilder();
    }

    public static class PurchaseOrderItemBuilder {
        private Long id;
        private PurchaseOrder purchaseOrder;
        private Long productId;
        private String productName;
        private String description;
        private String hsnCode;
        private BigDecimal quantity;
        private String unit = "pcs";
        private BigDecimal unitPrice;
        private BigDecimal discountValue = BigDecimal.ZERO;
        private BigDecimal gstPercent = BigDecimal.ZERO;
        private BigDecimal taxableAmount;
        private BigDecimal gstAmount;
        private BigDecimal totalAmount;

        public PurchaseOrderItemBuilder id(Long id) { this.id = id; return this; }
        public PurchaseOrderItemBuilder purchaseOrder(PurchaseOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; return this; }
        public PurchaseOrderItemBuilder productId(Long productId) { this.productId = productId; return this; }
        public PurchaseOrderItemBuilder productName(String productName) { this.productName = productName; return this; }
        public PurchaseOrderItemBuilder description(String description) { this.description = description; return this; }
        public PurchaseOrderItemBuilder hsnCode(String hsnCode) { this.hsnCode = hsnCode; return this; }
        public PurchaseOrderItemBuilder quantity(BigDecimal quantity) { this.quantity = quantity; return this; }
        public PurchaseOrderItemBuilder unit(String unit) { this.unit = unit; return this; }
        public PurchaseOrderItemBuilder unitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; return this; }
        public PurchaseOrderItemBuilder discountValue(BigDecimal discountValue) { this.discountValue = discountValue; return this; }
        public PurchaseOrderItemBuilder gstPercent(BigDecimal gstPercent) { this.gstPercent = gstPercent; return this; }
        public PurchaseOrderItemBuilder taxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount; return this; }
        public PurchaseOrderItemBuilder gstAmount(BigDecimal gstAmount) { this.gstAmount = gstAmount; return this; }
        public PurchaseOrderItemBuilder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }

        public PurchaseOrderItem build() {
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.id = this.id;
            item.purchaseOrder = this.purchaseOrder;
            item.productId = this.productId;
            item.productName = this.productName;
            item.description = this.description;
            item.hsnCode = this.hsnCode;
            item.quantity = this.quantity;
            item.unit = this.unit != null ? this.unit : "pcs";
            item.unitPrice = this.unitPrice;
            item.discountValue = this.discountValue != null ? this.discountValue : BigDecimal.ZERO;
            item.gstPercent = this.gstPercent != null ? this.gstPercent : BigDecimal.ZERO;
            item.taxableAmount = this.taxableAmount;
            item.gstAmount = this.gstAmount;
            item.totalAmount = this.totalAmount;
            return item;
        }
    }
}


