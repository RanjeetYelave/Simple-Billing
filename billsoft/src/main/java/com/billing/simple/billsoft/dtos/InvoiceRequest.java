package com.billing.simple.billsoft.dtos;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvoiceRequest {

    private Long customerId;
    private String notes;

    /**
     * Optional invoice-level discount (applied on subtotal after per-item discounts)
     * type: "PERCENT" or "VALUE"
     */
    private Discount invoiceDiscount;

    private List<InvoiceRequestItem> items;

    @Getter
    @Setter
    public static class Discount {
        private String type; // "PERCENT" or "VALUE"
        private Double value; // value or percent
    }
}
