package com.billing.simple.billsoft.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvoiceRequestItem {

    private Long productId;

    private Integer qty;
    private String unit;

    private Double pricePerUnit;
    private Double amountWithoutTax;

    private String discountType;        // "PERCENT" or "VALUE"
    private Double discountValue;
    private Double discountPercent;

    private Double taxableAmount;

    private Double gstPercent;
    private Double gstAmount;

    private Double lineTotal;           // Final line amount
}
