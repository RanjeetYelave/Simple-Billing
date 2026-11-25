package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable audit receipt entity recording a physical goods receipt against a Purchase Order.
 * Supports partial receiving across multiple distinct receipt transactions.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "purchase_order_receipts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_po_receipt_idempotency", columnNames = {"firm_id", "idempotency_key"})
        },
        indexes = {
                @Index(name = "idx_po_receipt_poid", columnList = "purchase_order_id"),
                @Index(name = "idx_po_receipt_firm", columnList = "firm_id")
        }
)
public class PurchaseOrderReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "firm_id", nullable = false)
    private Long firmId;

    @Column(name = "purchase_order_id", nullable = false)
    private Long purchaseOrderId;

    @Column(name = "receipt_number", nullable = false, length = 50)
    private String receiptNumber;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(name = "received_by", length = 100)
    private String receivedBy;

    @Column(length = 1000)
    private String notes;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<PurchaseOrderReceiptItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (receiptDate == null) {
            receiptDate = LocalDate.now();
        }
    }

    public void addItem(PurchaseOrderReceiptItem item) {
        if (items == null) {
            items = new ArrayList<>();
        }
        items.add(item);
        item.setReceipt(this);
    }
}
