package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Defines a rule-based price adjustment for a specific product category and pricing profile.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "category_pricing_rules", uniqueConstraints = {
        @UniqueConstraint(name = "uq_cat_rule_firm_cat_profile", columnNames = {"firm_id", "category_name", "pricing_profile_id"})
}, indexes = {
        @Index(name = "idx_cat_pricing_rules_lookup", columnList = "firm_id, category_name")
})
public class CategoryPricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "firm_id", nullable = false)
    private Long firmId;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "pricing_profile_id", nullable = false)
    private Long pricingProfileId;

    /**
     * Adjustment type: PERCENT or FIXED
     */
    @Builder.Default
    @Column(name = "adjustment_type", nullable = false, length = 20)
    private String adjustmentType = "PERCENT";

    /**
     * Adjustment value (e.g. 5.00 for +5%, -10.00 for -10%, 500.00 for +₹500, -250.00 for -₹250)
     */
    @Builder.Default
    @Column(name = "adjustment_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal adjustmentValue = BigDecimal.ZERO;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
        if (adjustmentType == null) adjustmentType = "PERCENT";
        adjustmentType = adjustmentType.trim().toUpperCase();
        if (adjustmentValue == null) adjustmentValue = BigDecimal.ZERO;
        if (categoryName != null) categoryName = categoryName.trim();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        if (adjustmentType != null) adjustmentType = adjustmentType.trim().toUpperCase();
        if (categoryName != null) categoryName = categoryName.trim();
    }
}
