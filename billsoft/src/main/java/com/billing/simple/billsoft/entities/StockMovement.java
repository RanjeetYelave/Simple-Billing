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
@Table(name = "stock_movements")
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
}
