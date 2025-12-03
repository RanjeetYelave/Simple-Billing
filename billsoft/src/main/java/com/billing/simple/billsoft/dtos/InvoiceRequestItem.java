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

    /* -------------------------------------------------------------
       QUANTITY & UNIT
    ------------------------------------------------------------- */
    private Integer qty;
    private String unit; // pcs, kg, box, etc.

    /* -------------------------------------------------------------
       PRICING
       If null → backend falls back to Product.price
    ------------------------------------------------------------- */
    private BigDecimal pricePerUnit;

    /* -------------------------------------------------------------
       DISCOUNT
       Backend logic:
           If percent > 0 → percent discount applies
           Else if discountValue > 0 → value discount applies
    ------------------------------------------------------------- */
    private BigDecimal discountPercent; // % discount
    private BigDecimal discountValue;   // ₹ discount amount

    /* Optional UI helper: "PERCENT" or "VALUE"
       Backend does not rely on this field in calculations */
    private String discountType;

    /* -------------------------------------------------------------
       GST
       If null → backend uses Product.gstPercentage
    ------------------------------------------------------------- */
    private BigDecimal gstPercent;
}
