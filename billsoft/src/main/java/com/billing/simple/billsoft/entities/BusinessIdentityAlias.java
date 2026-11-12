package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Searchable alias entity for Customer and Party multi-role identities.
 * Preserves past canonical names and explicitly defined trading/informal aliases.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "business_identity_aliases", indexes = {
    @Index(name = "idx_bia_firm_norm", columnList = "firmId, normalizedValue"),
    @Index(name = "idx_bia_firm_link", columnList = "firmId, linkId"),
    @Index(name = "idx_bia_firm_cust", columnList = "firmId, customerId"),
    @Index(name = "idx_bia_firm_party", columnList = "firmId, partyId")
})
public class BusinessIdentityAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false, length = 255)
    private String aliasValue;

    @Column(nullable = false, length = 255)
    private String normalizedValue;

    @Column(nullable = false, length = 50)
    private String aliasType; // "PREVIOUS_NAME", "MANUAL_ALIAS", "TRADE_NAME", "LEGAL_NAME"

    private Long linkId;

    private Long customerId;

    private Long partyId;

    @Column(length = 30)
    private String ownerRole; // "CUSTOMER", "PARTY", "SHARED", "RETIRED"

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 100)
    private String createdBy;

    @Column(length = 500)
    private String resolutionNotes;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.active == null) {
            this.active = Boolean.TRUE;
        }
        if (this.aliasValue != null && (this.normalizedValue == null || this.normalizedValue.isBlank())) {
            this.normalizedValue = normalize(this.aliasValue);
        }
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}

