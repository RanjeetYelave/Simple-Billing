package com.billing.simple.billsoft.dtos;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductBulkRowDto {
    private String name;
    private String sku;
    private String barcode;
    private String category;
    private String itemType; // GOODS or SERVICE
    private BigDecimal price; // Base / List selling price
    private BigDecimal costPrice; // Purchase / cost price
    private String unit;
    private String hsnCode;
    private BigDecimal gstPercentage;
    private BigDecimal initialStock; // Opening stock for physical goods
    private BigDecimal minStockLevel;
    private String description;
    private Boolean inInventory;
    private Long preferredPartyId;
}
