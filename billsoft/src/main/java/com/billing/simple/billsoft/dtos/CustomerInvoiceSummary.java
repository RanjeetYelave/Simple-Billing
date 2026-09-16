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

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(String invoiceDate) { this.invoiceDate = invoiceDate; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public Boolean getPaid() { return paid; }
    public void setPaid(Boolean paid) { this.paid = paid; }
}

