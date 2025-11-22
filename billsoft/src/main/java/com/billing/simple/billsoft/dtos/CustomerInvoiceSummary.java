package com.billing.simple.billsoft.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerInvoiceSummary {

    private Long invoiceId;
    private String invoiceNumber;
    private String invoiceDate;   // ISO string (from LocalDateTime.toString)
    private Double totalAmount;
    private Boolean paid;
}
