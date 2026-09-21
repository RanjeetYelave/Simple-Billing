package com.billing.simple.billsoft.dtos;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MergeRequestDto {
    private String primaryType; // "PRODUCT" or "SAVED_ITEM"
    private Long primaryId;
    private String secondaryType; // "PRODUCT" or "SAVED_ITEM"
    private Long secondaryId;

    // Field Reconciliation Choices
    private String nameResolution; // "KEEP_PRIMARY", "USE_SECONDARY", or custom string
    private String skuResolution; // "KEEP_PRIMARY", "USE_SECONDARY"
    private String sellingPriceResolution; // "KEEP_PRIMARY", "USE_SECONDARY", "CUSTOM"
    private BigDecimal customSellingPrice;
    private String purchasePriceResolution; // "KEEP_PRIMARY", "USE_SECONDARY", "CUSTOM"
    private BigDecimal customPurchasePrice;
    private String stockStrategy; // "ADD", "KEEP_PRIMARY", "KEEP_SECONDARY", "MANUAL"
    private BigDecimal manualStockQuantity;
    private String gstResolution; // "KEEP_PRIMARY", "USE_SECONDARY"
    private String unitResolution; // "KEEP_PRIMARY", "USE_SECONDARY"
    private String categoryResolution; // "KEEP_PRIMARY", "USE_SECONDARY"
}
