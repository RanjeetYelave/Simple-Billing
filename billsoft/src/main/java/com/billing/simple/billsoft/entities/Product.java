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
    @Column(precision = 15, scale = 2)
    private BigDecimal gstPercentage;

    // Optional preferred supplier/vendor
    @Column(name = "preferred_party_id")
    private Long preferredPartyId;

    @Column(nullable = false)
    private Long firmId;

    // Optional link if merged to another canonical Product
    @Column(name = "canonical_product_id")
    private Long canonicalProductId;

    @Builder.Default
    @Column(name = "is_archived")
    private Boolean isArchived = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.stockQuantity == null || this.stockQuantity.compareTo(BigDecimal.ZERO) < 0) {
            this.stockQuantity = BigDecimal.ZERO;
        }
        if (this.minStockLevel == null || this.minStockLevel.compareTo(BigDecimal.ZERO) < 0) {
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
        if (this.stockQuantity == null || this.stockQuantity.compareTo(BigDecimal.ZERO) < 0) {
            this.stockQuantity = BigDecimal.ZERO;
        }
        if (this.minStockLevel != null && this.minStockLevel.compareTo(BigDecimal.ZERO) < 0) {
            this.minStockLevel = BigDecimal.ZERO;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
    public BigDecimal getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(BigDecimal stockQuantity) { this.stockQuantity = stockQuantity; }
    public BigDecimal getMinStockLevel() { return minStockLevel; }
    public void setMinStockLevel(BigDecimal minStockLevel) { this.minStockLevel = minStockLevel; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }
    public BigDecimal getGstPercentage() { return gstPercentage; }
    public void setGstPercentage(BigDecimal gstPercentage) { this.gstPercentage = gstPercentage; }
    public Long getPreferredPartyId() { return preferredPartyId; }
    public void setPreferredPartyId(Long preferredPartyId) { this.preferredPartyId = preferredPartyId; }
    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }
    public Long getCanonicalProductId() { return canonicalProductId; }
    public void setCanonicalProductId(Long canonicalProductId) { this.canonicalProductId = canonicalProductId; }
    public Boolean getIsArchived() { return isArchived; }
    public void setIsArchived(Boolean isArchived) { this.isArchived = isArchived; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static ProductBuilder builder() {
        return new ProductBuilder();
    }

    public static class ProductBuilder {
        private Long id;
        private String name;
        private BigDecimal price;
        private BigDecimal costPrice;
        private BigDecimal stockQuantity = BigDecimal.ZERO;
        private BigDecimal minStockLevel = new BigDecimal("5.000");
        private String sku;
        private String barcode;
        private String category;
        private String itemType = "GOODS";
        private String description;
        private String unit = "pcs";
        private String hsnCode;
        private BigDecimal gstPercentage;
        private Long preferredPartyId;
        private Long firmId;
        private Long canonicalProductId;
        private Boolean isArchived = false;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public ProductBuilder id(Long id) { this.id = id; return this; }
        public ProductBuilder name(String name) { this.name = name; return this; }
        public ProductBuilder price(BigDecimal price) { this.price = price; return this; }
        public ProductBuilder costPrice(BigDecimal costPrice) { this.costPrice = costPrice; return this; }
        public ProductBuilder stockQuantity(BigDecimal stockQuantity) { this.stockQuantity = stockQuantity; return this; }
        public ProductBuilder minStockLevel(BigDecimal minStockLevel) { this.minStockLevel = minStockLevel; return this; }
        public ProductBuilder sku(String sku) { this.sku = sku; return this; }
        public ProductBuilder barcode(String barcode) { this.barcode = barcode; return this; }
        public ProductBuilder category(String category) { this.category = category; return this; }
        public ProductBuilder itemType(String itemType) { this.itemType = itemType; return this; }
        public ProductBuilder description(String description) { this.description = description; return this; }
        public ProductBuilder unit(String unit) { this.unit = unit; return this; }
        public ProductBuilder hsnCode(String hsnCode) { this.hsnCode = hsnCode; return this; }
        public ProductBuilder gstPercentage(BigDecimal gstPercentage) { this.gstPercentage = gstPercentage; return this; }
        public ProductBuilder preferredPartyId(Long preferredPartyId) { this.preferredPartyId = preferredPartyId; return this; }
        public ProductBuilder firmId(Long firmId) { this.firmId = firmId; return this; }
        public ProductBuilder canonicalProductId(Long canonicalProductId) { this.canonicalProductId = canonicalProductId; return this; }
        public ProductBuilder isArchived(Boolean isArchived) { this.isArchived = isArchived; return this; }
        public ProductBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public ProductBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public Product build() {
            Product p = new Product();
            p.id = this.id;
            p.name = this.name;
            p.price = this.price;
            p.costPrice = this.costPrice;
            p.stockQuantity = this.stockQuantity;
            p.minStockLevel = this.minStockLevel;
            p.sku = this.sku;
            p.barcode = this.barcode;
            p.category = this.category;
            p.itemType = this.itemType;
            p.description = this.description;
            p.unit = this.unit;
            p.hsnCode = this.hsnCode;
            p.gstPercentage = this.gstPercentage;
            p.preferredPartyId = this.preferredPartyId;
            p.firmId = this.firmId;
            p.canonicalProductId = this.canonicalProductId;
            p.isArchived = this.isArchived != null ? this.isArchived : false;
            p.createdAt = this.createdAt;
            p.updatedAt = this.updatedAt;
            return p;
        }
    }
}

