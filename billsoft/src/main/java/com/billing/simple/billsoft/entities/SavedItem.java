package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "saved_items", indexes = {
    @Index(name = "idx_saved_item_firm_norm", columnList = "firmId, normalizedName"),
    @Index(name = "idx_saved_item_firm_usage", columnList = "firmId, usageCount, lastUsedAt")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SavedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String normalizedName;

    @Builder.Default
    @Column(length = 50)
    private String unit = "pcs";

    @Column(precision = 15, scale = 2)
    private BigDecimal sellingPrice;

    @Column(length = 50)
    private String hsnCode;

    @Column(precision = 15, scale = 2)
    private BigDecimal gstPercentage;

    @Column(length = 100)
    private String category;

    @Column(length = 1000)
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private Integer usageCount = 1;

    private LocalDateTime firstUsedAt;
    private LocalDateTime lastUsedAt;

    // Optional link if promoted or merged to a Product
    @Column(name = "canonical_product_id")
    private Long canonicalProductId;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isArchived = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.firstUsedAt == null) {
            this.firstUsedAt = now;
        }
        if (this.lastUsedAt == null) {
            this.lastUsedAt = now;
        }
        if (this.usageCount == null || this.usageCount < 1) {
            this.usageCount = 1;
        }
        if (this.unit == null || this.unit.trim().isEmpty()) {
            this.unit = "pcs";
        }
        if (this.isArchived == null) {
            this.isArchived = false;
        }
        if (this.normalizedName == null && this.name != null) {
            this.normalizedName = normalizeText(this.name);
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.normalizedName == null && this.name != null) {
            this.normalizedName = normalizeText(this.name);
        }
    }

    public static String normalizeText(String input) {
        if (input == null) return "";
        return input.toLowerCase()
                .replaceAll("[^a-z0-9]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
