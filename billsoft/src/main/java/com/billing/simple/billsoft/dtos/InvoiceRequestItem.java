package com.billing.simple.billsoft.dtos;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
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
    private String unit;   // pcs, kg, box, etc.

    /* -------------------------------------------------------------
       PRICING (BigDecimal)
       If null → backend falls back to Product.price
       ------------------------------------------------------------- */
    private BigDecimal pricePerUnit;

    /* -------------------------------------------------------------
       DISCOUNT (BigDecimal)
       Precedence:
           discountPercent > discountValue
       discountType is optional (UI convenience only)
       ------------------------------------------------------------- */
    private String discountType;            // "PERCENT" / "VALUE" / null
    private BigDecimal discountValue;       // flat discount ₹
    private BigDecimal discountPercent;     // percent 0–100

    /* -------------------------------------------------------------
       GST (BigDecimal)
       If null → backend uses Product.gstPercentage
       ------------------------------------------------------------- */
    private BigDecimal gstPercent;
}
