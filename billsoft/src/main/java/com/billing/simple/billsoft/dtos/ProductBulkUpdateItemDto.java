package com.billing.simple.billsoft.dtos;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductBulkUpdateItemDto {
    private Long productId;
    private String name;
    private String sku;
    private String barcode;
    private String category;
    private String itemType;
    private BigDecimal price;
    private BigDecimal costPrice;
    private String unit;
    private String hsnCode;
    private BigDecimal gstPercentage;
    private BigDecimal minStockLevel;
    private String description;
    private Long preferredPartyId;
    private Boolean inInventory;
    private Boolean trackInInventory;
    private BigDecimal openingStock;
}
