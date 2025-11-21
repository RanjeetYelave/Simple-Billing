package com.billing.simple.billsoft.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvoiceRequestItem {

    /**
     * productId must be provided (or UI can auto-create product before sending and return id)
     */
    private Long productId;

    private Integer qty;
    private String unit;

    /**
     * Price per unit that UI presents (server will use this as base price for calculations,
     * but you may also choose to fall back to product.price if null)
     */
    private Double pricePerUnit;

    /**
     * Optional per-item discount (if present). Discount reason: UI may send either percent or value.
     * If both present, discountPercent takes precedence if > 0.
     */
    private String discountType; // "PERCENT" or "VALUE" or null
    private Double discountValue;
    private Double discountPercent;

    /**
     * Optional GST percentage for the item. If absent server will fall back to product.gstPercentage.
     */
    private Double gstPercent;
}
