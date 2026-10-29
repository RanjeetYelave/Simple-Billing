package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Explicit 1:1 bidirectional mapping between an existing Customer and an existing Party (Supplier)
 * within the same tenant firm.
 * Neither record is merged or deleted; their separate financial histories and IDs remain unchanged.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "business_party_links", uniqueConstraints = {
    @UniqueConstraint(name = "uk_bpl_firm_customer", columnNames = {"firmId", "customerId"}),
    @UniqueConstraint(name = "uk_bpl_firm_party", columnNames = {"firmId", "partyId"})
}, indexes = {
    @Index(name = "idx_bpl_firm_cust", columnList = "firmId, customerId"),
    @Index(name = "idx_bpl_firm_party", columnList = "firmId, partyId")
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

    @Column(nullable = false)
    private LocalDateTime linkedAt;

    @Column(length = 100)
    private String linkedBy;

    @Column(length = 500)
    private String notes;

    @PrePersist
    public void prePersist() {
        if (this.linkedAt == null) {
            this.linkedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }

    public LocalDateTime getLinkedAt() { return linkedAt; }
    public void setLinkedAt(LocalDateTime linkedAt) { this.linkedAt = linkedAt; }

    public String getLinkedBy() { return linkedBy; }
    public void setLinkedBy(String linkedBy) { this.linkedBy = linkedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
