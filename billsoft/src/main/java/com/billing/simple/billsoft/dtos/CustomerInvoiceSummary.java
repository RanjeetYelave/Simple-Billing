package com.billing.simple.billsoft.dtos;

import lombok.Data;

/**
 * Per-invoice summary used inside customer analytics.
 */
@Data
public class CustomerInvoiceSummary {

    private Long invoiceId;
    private String invoiceNumber;
    private String invoiceDate;

    private Double totalAmount;
    private Boolean paid;
}
