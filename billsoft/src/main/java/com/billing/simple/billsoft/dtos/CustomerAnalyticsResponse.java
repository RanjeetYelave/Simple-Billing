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

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public Double getTotalBusiness() { return totalBusiness; }
    public void setTotalBusiness(Double totalBusiness) { this.totalBusiness = totalBusiness; }

    public Double getTotalPaid() { return totalPaid; }
    public void setTotalPaid(Double totalPaid) { this.totalPaid = totalPaid; }

    public Double getTotalPending() { return totalPending; }
    public void setTotalPending(Double totalPending) { this.totalPending = totalPending; }

    public Long getInvoiceCount() { return invoiceCount; }
    public void setInvoiceCount(Long invoiceCount) { this.invoiceCount = invoiceCount; }

    public List<CustomerInvoiceSummary> getInvoices() { return invoices; }
    public void setInvoices(List<CustomerInvoiceSummary> invoices) { this.invoices = invoices; }

    // Compatibility methods for legacy test expectations
    public void setTotal(int total) {
        this.totalBusiness = (double) total;
    }

    public Double getTotal() {
        return this.totalBusiness;
    }
}

