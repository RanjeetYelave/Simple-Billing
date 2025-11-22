package com.billing.simple.billsoft.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvoiceRequestItem {

    /**
     * productId must be provided
     * (or UI can auto-create product before sending and return id)
     */
    private Long productId;

    private Integer qty;
    private String unit;

    /**
     * Price per unit that UI presents.
     * Server will use this as base price for calculations,
     * but you may also choose to fall back to product.price if null.
     */
    private Double pricePerUnit;

    /**
     * Optional per-item discount (if present).
     * If both discountValue and discountPercent are present,
     * discountPercent takes precedence if > 0.
     */
    private String discountType;   // "PERCENT" or "VALUE" or null
    private Double discountValue;  // flat amount
    private Double discountPercent; // percentage

    /**
     * Optional GST percentage for the item.
     * If absent server will fall back to product.gstPercentage.
     */
    private Double gstPercent;
}
