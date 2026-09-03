package com.billing.simple.billsoft.dtos;

import lombok.Data;
import java.math.BigDecimal;

/**
 * Invoice line item request model.
 * UI must send productId + qty. Other values are optional.
 */
@Data
public class InvoiceRequestItem {

    /**
     * Must be provided by UI when selecting a product.
     * For custom/manual items, UI must create a temporary Product first.
     */
    private Long productId;
    private String productName;

    /* -------------------------------------------------------------
       QUANTITY & UNIT
    ------------------------------------------------------------- */
    private Integer qty;
    private String unit; // pcs, kg, box, etc.
    private String hsnCode; // HSN or SAC code

    /* -------------------------------------------------------------
       PRICING
       If null → backend falls back to Product.price
    ------------------------------------------------------------- */
    private BigDecimal pricePerUnit;

    /* -------------------------------------------------------------
       DISCOUNT (ITEM-LEVEL)
       VALUE ONLY (₹ discount amount per line)
    ------------------------------------------------------------- */
    private BigDecimal discountValue;   // ₹ discount amount

    /* -------------------------------------------------------------
       GST
       If null → backend uses Product.gstPercentage
    ------------------------------------------------------------- */
    private BigDecimal gstPercent;
}
