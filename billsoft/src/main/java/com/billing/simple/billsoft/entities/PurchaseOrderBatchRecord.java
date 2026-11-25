package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persisted record of multi-vendor purchase order batch execution.
 * Guarantees durable idempotency across server restarts, network retries, and rapid submissions.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "purchase_order_batch_records",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_pobatch_firm_supplier_op", columnNames = {"firmId", "supplierOpId"})
    },
    indexes = {
        @Index(name = "idx_pobatch_firm_req", columnList = "firmId, clientRequestId"),
        @Index(name = "idx_pobatch_firm_supplier_op", columnList = "firmId, supplierOpId")
    }
)
public class PurchaseOrderBatchRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false, length = 150)
    private String clientRequestId;

    @Column(nullable = false, length = 200)
    private String supplierOpId; // e.g. clientRequestId + "_party_" + partyId

    @Column(nullable = false)
    private Long partyId;

    private String partyName;

    @Column(nullable = false, length = 30)
    private String status; // SUCCESS, FAILED, IN_PROGRESS

    private Long purchaseOrderId; // References created PurchaseOrder if SUCCESS

    private String poNumber; // Sequential PO number if SUCCESS

    @Column(length = 2000)
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = "IN_PROGRESS";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
