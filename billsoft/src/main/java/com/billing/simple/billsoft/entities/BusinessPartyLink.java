package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Explicit 1:1 bidirectional mapping between an existing Customer and an existing Party (Supplier)
 * within the same tenant firm.
 * Neither record is merged or deleted; their separate financial histories and IDs remain unchanged.
 * Supports soft unlinking while preserving relationship history.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "business_party_links", uniqueConstraints = {
    @UniqueConstraint(name = "uk_bpl_firm_act_cust", columnNames = {"firmId", "activeCustomerId"}),
    @UniqueConstraint(name = "uk_bpl_firm_act_party", columnNames = {"firmId", "activePartyId"})
}, indexes = {
    @Index(name = "idx_bpl_firm_cust", columnList = "firmId, customerId"),
    @Index(name = "idx_bpl_firm_party", columnList = "firmId, partyId"),
    @Index(name = "idx_bpl_firm_status", columnList = "firmId, status")
})
public class BusinessPartyLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private Long partyId;

    /**
     * Active counterpart pointer: Populated for active links to enforce strict 1:1 active uniqueness.
     * Cleared to NULL on soft-unlinking so historical version rows are preserved without colliding.
     */
    private Long activeCustomerId;

    /**
     * Active counterpart pointer: Populated for active links to enforce strict 1:1 active uniqueness.
     * Cleared to NULL on soft-unlinking so historical version rows are preserved without colliding.
     */
    private Long activePartyId;

    @Column(nullable = false)
    private LocalDateTime linkedAt;

    @Column(length = 100)
    private String linkedBy;

    @Column(length = 500)
    private String notes;

    @Builder.Default
    @Column(length = 30)
    private String status = "ACTIVE"; // "ACTIVE", "CUSTOMER_DISABLED", "PARTY_DISABLED", "BOTH_DISABLED", "UNLINK_PENDING", "UNLINKED"

    @Builder.Default
    private Boolean customerRoleActive = Boolean.TRUE;

    @Builder.Default
    private Boolean partyRoleActive = Boolean.TRUE;

    private LocalDateTime statusUpdatedAt;

    @Column(length = 300)
    private String disabledReason;

    private LocalDateTime unlinkedAt;

    @Column(length = 100)
    private String unlinkedBy;

    @Column(length = 500)
    private String unlinkReason;

    @Builder.Default
    @Column(nullable = false)
    private Integer relationshipVersion = 1;

    @PrePersist
    public void prePersist() {
        if (this.linkedAt == null) {
            this.linkedAt = LocalDateTime.now();
        }
        if (this.customerRoleActive == null) {
            this.customerRoleActive = Boolean.TRUE;
        }
        if (this.partyRoleActive == null) {
            this.partyRoleActive = Boolean.TRUE;
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = "ACTIVE";
        }
        if (this.relationshipVersion == null) {
            this.relationshipVersion = 1;
        }
        syncActiveColumns();
    }

    @PreUpdate
    public void preUpdate() {
        syncActiveColumns();
    }

    public void syncActiveColumns() {
        if ("UNLINKED".equalsIgnoreCase(this.status)) {
            this.activeCustomerId = null;
            this.activePartyId = null;
        } else {
            if (this.activeCustomerId == null && this.customerId != null) {
                this.activeCustomerId = this.customerId;
            }
            if (this.activePartyId == null && this.partyId != null) {
                this.activePartyId = this.partyId;
            }
        }
    }
}
