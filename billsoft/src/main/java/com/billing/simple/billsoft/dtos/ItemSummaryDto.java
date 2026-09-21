package com.billing.simple.billsoft.dtos;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemSummaryDto {
    private String sourceType; // "PRODUCT" or "SAVED_ITEM"
    private Long id;
    private String name;
    private String sku;
    private String barcode;
    private String unit;
    private BigDecimal price;
    private BigDecimal costPrice;
    private BigDecimal stockQuantity;
    private String hsnCode;
    private BigDecimal gstPercentage;
    private String category;
    private Integer usageCount;
}
