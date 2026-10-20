package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Defines a pricing tier / model for a specific firm (e.g. RETAIL, WHOLESALE, CUSTOM_1).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "pricing_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uq_pricing_profile_firm_code", columnNames = {"firm_id", "code"})
})
public class PricingProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long version;

    @Column(name = "firm_id", nullable = false)
    private Long firmId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(length = 255)
    private String description;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
        if (isDefault == null) isDefault = false;
        if (isActive == null) isActive = true;
        if (code != null) code = code.trim().toUpperCase();
        if (name != null) name = name.trim();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (code != null) code = code.trim().toUpperCase();
        if (name != null) name = name.trim();
    }
}
