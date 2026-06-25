package com.billing.simple.billsoft.dtos;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Analytics data for a single customer.
 */
@Getter
@Setter
public class CustomerAnalyticsResponse {

    private Long customerId;
    private String customerName;

    private Double totalBusiness;   // sum of totalAmount from all invoices
    private Double totalPaid;       // sum of paid invoices
    private Double totalPending;    // totalBusiness - totalPaid
    private Long invoiceCount;

    private List<CustomerInvoiceSummary> invoices;

    // Compatibility methods for legacy test expectations
    public void setTotal(int total) {
        this.totalBusiness = (double) total;
    }

    public Double getTotal() {
        return this.totalBusiness;
    }
}
