package com.billing.simple.billsoft.dtos;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvoiceUpdateRequest {

    private Long customerId;

    /**
     * Optional ISO local date-time string
     * e.g. "2025-11-21T16:30" or "2025-11-21T16:30:00"
     */
    private String invoiceDate;

    private String notes;

    /**
     * Optional invoice-level discount (applied on subtotal after per-item discounts)
     */
    private InvoiceRequest.Discount invoiceDiscount;

    /**
     * Mark invoice as paid / unpaid.
     * If null, service should keep existing value.
     */
    private Boolean paid;

    /**
     * NEW LIST replaces previous items completely.
     * UI sends minimal fields, backend will recalc amounts.
     */
    private List<InvoiceRequestItem> items;
}
