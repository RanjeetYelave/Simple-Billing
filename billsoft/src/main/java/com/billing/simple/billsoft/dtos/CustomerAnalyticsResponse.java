package com.billing.simple.billsoft.dtos;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerAnalyticsResponse {

    private Long customerId;
    private String customerName;

    private Double totalBusiness;   // sum of totalAmount for all invoices
    private Double totalPaid;       // sum of totalAmount for paid invoices
    private Double totalPending;    // totalBusiness - totalPaid
    private Long invoiceCount;      // number of invoices

    private List<CustomerInvoiceSummary> invoices;
}
