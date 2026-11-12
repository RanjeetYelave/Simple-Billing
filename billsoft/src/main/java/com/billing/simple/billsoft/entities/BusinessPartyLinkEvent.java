package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit trail event tracking the entire lifecycle of a Customer–Party business identity link.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "business_party_link_events", indexes = {
    @Index(name = "idx_bple_firm_link", columnList = "firmId, linkId"),
    @Index(name = "idx_bple_firm_time", columnList = "firmId, timestamp DESC")
})
public class BusinessPartyLinkEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    private Long linkId;

    private Integer relationshipVersion;

    private Long customerId;

    private Long partyId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 100)
    private String actor;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String beforeValue;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String afterValue;

    @Column(length = 500)
    private String reason;

    @PrePersist
    public void prePersist() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }
}

