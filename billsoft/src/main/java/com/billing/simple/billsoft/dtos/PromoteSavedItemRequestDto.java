package com.billing.simple.billsoft.dtos;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoteSavedItemRequestDto {
    private Long savedItemId;
    private String name;
    private String category;
    private String itemType; // "GOODS" or "SERVICE"
    private String unit;
    private BigDecimal price; // Selling price
    private BigDecimal costPrice; // Purchase / Cost price
    private BigDecimal stockQuantity; // Opening stock
    private BigDecimal minStockLevel;
    private String sku;
    private String barcode;
    private String hsnCode;
    private BigDecimal gstPercentage;
    private String description;
    
    // If the user chooses to link to an existing Inventory Product instead of creating a new one:
    private Long existingProductId;
}
