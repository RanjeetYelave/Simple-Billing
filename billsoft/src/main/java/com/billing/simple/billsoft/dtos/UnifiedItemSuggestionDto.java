package com.billing.simple.billsoft.dtos;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnifiedItemSuggestionDto {
    private String id; // Unique string key for React lists: e.g. "PROD_101" or "SAVED_502"
    private Long rawId; // Numeric ID of the entity
    private String source; // "INVENTORY" or "SAVED_ITEM"
    private String name;
    private String sku;
    private String unit;
    private BigDecimal price;
    private BigDecimal costPrice;
    private BigDecimal stockQuantity;
    private BigDecimal minStockLevel;
    private String itemType; // "GOODS" or "SERVICE"
    private String hsnCode;
    private BigDecimal gstPercentage;
    private String category;
    private String description;
    private Integer usageCount;
    private String badge; // e.g. "Inventory · Stock: 42" or "Saved Item · Not in Inventory"
    private Long canonicalProductId;
}
