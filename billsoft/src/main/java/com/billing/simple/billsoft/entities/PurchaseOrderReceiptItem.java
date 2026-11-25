package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Line item in an immutable PurchaseOrderReceipt.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "purchase_order_receipt_items",
        indexes = {
                @Index(name = "idx_po_rcpt_item_rcpt", columnList = "receipt_id"),
                @Index(name = "idx_po_rcpt_item_prod", columnList = "product_id")
        }
)
public class PurchaseOrderReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    @JsonBackReference
    private PurchaseOrderReceipt receipt;

    @Column(name = "purchase_order_item_id")
    private Long purchaseOrderItemId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(name = "received_quantity", precision = 12, scale = 3, nullable = false)
    private BigDecimal receivedQuantity;

    @Builder.Default
    @Column(length = 20)
    private String unit = "pcs";
}
