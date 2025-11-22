package com.billing.simple.billsoft.dtos;

import lombok.Data;

@Data
public class FirmInvoiceSummary {

    private Long invoiceId;
    private String invoiceNumber;
    private String date;

    private Double amount;
    private Boolean paid;

    private Long customerId;
    private String customerName;
}
