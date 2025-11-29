package com.billing.simple.billsoft.dtos;

import java.time.LocalDate;
import java.util.List;

public class FirmStatementResponse {
    private LocalDate from;
    private LocalDate to;
    private Double totalBilled;
    private Double totalPaid;
    private Double outstanding;
    private Double totalTax;
    private Integer invoiceCount;
    private List<GstSummaryItem> gstSummary;
    // optional: invoice rows if you want them
    // private List<StatementEntry> entries;

    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }

    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }

    public Double getTotalBilled() { return totalBilled; }
    public void setTotalBilled(Double totalBilled) { this.totalBilled = totalBilled; }

    public Double getTotalPaid() { return totalPaid; }
    public void setTotalPaid(Double totalPaid) { this.totalPaid = totalPaid; }

    public Double getOutstanding() { return outstanding; }
    public void setOutstanding(Double outstanding) { this.outstanding = outstanding; }

    public Double getTotalTax() { return totalTax; }
    public void setTotalTax(Double totalTax) { this.totalTax = totalTax; }

    public Integer getInvoiceCount() { return invoiceCount; }
    public void setInvoiceCount(Integer invoiceCount) { this.invoiceCount = invoiceCount; }

    public List<GstSummaryItem> getGstSummary() { return gstSummary; }
    public void setGstSummary(List<GstSummaryItem> gstSummary) { this.gstSummary = gstSummary; }
}
