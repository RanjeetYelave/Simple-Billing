package com.billing.simple.billsoft.dtos;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvoiceUpdateRequest {

    private Long customerId;
    private String invoiceDate; // optional ISO local date-time string (e.g. "2025-11-21T16:30" or "2025-11-21T16:30:00")
    private String notes;

    /**
     * Optional invoice-level discount (applied on subtotal after per-item discounts)
     */
    private InvoiceRequest.Discount invoiceDiscount;

    /**
     * NEW LIST replaces previous items
     */
    private List<InvoiceRequestItem> items;
}
