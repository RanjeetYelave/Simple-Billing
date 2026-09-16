package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Audit ledger entry recording every change in inventory stock quantity.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "stock_movements", indexes = {
    @Index(name = "idx_stock_mov_firm_prod_date", columnList = "firmId, productId, createdAt DESC"),
    @Index(name = "idx_stock_mov_firm_date", columnList = "firmId, createdAt DESC")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Long firmId;

    /**
     * INITIAL_STOCK, INVOICE_SALE, PURCHASE_RECEIPT, SALE_RETURN, PURCHASE_RETURN, MANUAL_ADJUSTMENT, INVOICE_CANCELLED
     */
    @Column(nullable = false, length = 50)
    private String movementType;

    // Positive if stock added, negative if deducted
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityChange;

    @Column(precision = 12, scale = 3)
    private BigDecimal previousStock;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal newStock;

    // INVOICE, PURCHASE_ORDER, MANUAL
    @Column(length = 50)
    private String referenceType;

    // Reference ID / Number (e.g. "INV-2026-0012", "PO-2026-0004", or entity ID)
    @Column(length = 100)
    private String referenceId;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public String getMovementType() { return movementType; }
    public void setMovementType(String movementType) { this.movementType = movementType; }

    public BigDecimal getQuantityChange() { return quantityChange; }
    public void setQuantityChange(BigDecimal quantityChange) { this.quantityChange = quantityChange; }

    public BigDecimal getPreviousStock() { return previousStock; }
    public void setPreviousStock(BigDecimal previousStock) { this.previousStock = previousStock; }

    public BigDecimal getNewStock() { return newStock; }
    public void setNewStock(BigDecimal newStock) { this.newStock = newStock; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static StockMovementBuilder builder() {
        return new StockMovementBuilder();
    }

    public static class StockMovementBuilder {
        private Long id;
        private Long productId;
        private String productName;
        private Long firmId;
        private String movementType;
        private BigDecimal quantityChange;
        private BigDecimal previousStock;
        private BigDecimal newStock;
        private String referenceType;
        private String referenceId;
        private String note;
        private LocalDateTime createdAt;

        public StockMovementBuilder id(Long id) { this.id = id; return this; }
        public StockMovementBuilder productId(Long productId) { this.productId = productId; return this; }
        public StockMovementBuilder productName(String productName) { this.productName = productName; return this; }
        public StockMovementBuilder firmId(Long firmId) { this.firmId = firmId; return this; }
        public StockMovementBuilder movementType(String movementType) { this.movementType = movementType; return this; }
        public StockMovementBuilder quantityChange(BigDecimal quantityChange) { this.quantityChange = quantityChange; return this; }
        public StockMovementBuilder previousStock(BigDecimal previousStock) { this.previousStock = previousStock; return this; }
        public StockMovementBuilder newStock(BigDecimal newStock) { this.newStock = newStock; return this; }
        public StockMovementBuilder referenceType(String referenceType) { this.referenceType = referenceType; return this; }
        public StockMovementBuilder referenceId(String referenceId) { this.referenceId = referenceId; return this; }
        public StockMovementBuilder note(String note) { this.note = note; return this; }
        public StockMovementBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public StockMovement build() {
            StockMovement sm = new StockMovement();
            sm.id = this.id;
            sm.productId = this.productId;
            sm.productName = this.productName;
            sm.firmId = this.firmId;
            sm.movementType = this.movementType;
            sm.quantityChange = this.quantityChange;
            sm.previousStock = this.previousStock;
            sm.newStock = this.newStock;
            sm.referenceType = this.referenceType;
            sm.referenceId = this.referenceId;
            sm.note = this.note;
            sm.createdAt = this.createdAt;
            return sm;
        }
    }
}


