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
@Table(name = "products")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Selling Price
    @Column(precision = 15, scale = 2)
    private BigDecimal price;

    // Purchase / Cost Price (for COGS and Profitability)
    @Column(precision = 15, scale = 2)
    private BigDecimal costPrice;

    // Inventory Stock Quantity (e.g. 50.000 pcs, 12.500 kg)
    @Builder.Default
    @Column(precision = 12, scale = 3)
    private BigDecimal stockQuantity = BigDecimal.ZERO;

    // Minimum Stock Alert Threshold
    @Builder.Default
    @Column(precision = 12, scale = 3)
    private BigDecimal minStockLevel = new BigDecimal("5.000");

    // Stock Keeping Unit / Item Code
    @Column(length = 100)
    private String sku;

    // Barcode for scanners
    @Column(length = 100)
    private String barcode;

    // Category / Group (e.g. Electronics, Raw Materials, Spare Parts)
    @Column(length = 100)
    private String category;

    // GOODS (Physical Inventory) or SERVICE (Billable Time/Service)
    @Builder.Default
    @Column(length = 20)
    private String itemType = "GOODS";

    @Column(length = 1000)
    private String description;

    @Builder.Default
    @Column(length = 50)
    private String unit = "pcs"; // pcs, kg, litre etc.

    private String hsnCode; // HSN or SAC code

    // GST percentage as BigDecimal (e.g., 18.00)
    @Column(precision = 5, scale = 2)
    private BigDecimal gstPercentage;

    @Column(nullable = false)
    private Long firmId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.stockQuantity == null) {
            this.stockQuantity = BigDecimal.ZERO;
        }
        if (this.minStockLevel == null) {
            this.minStockLevel = new BigDecimal("5.000");
        }
        if (this.itemType == null || this.itemType.trim().isEmpty()) {
            this.itemType = "GOODS";
        }
        if (this.unit == null || this.unit.trim().isEmpty()) {
            this.unit = "pcs";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
