package com.billing.simple.billsoft.dtos;

import com.billing.simple.billsoft.entities.Customer;
import com.billing.simple.billsoft.entities.InvoicePayment;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer360Response {
    private Customer customer;
    private Double totalBilled;
    private Double totalPaid;
    private Double netBalance;
    private Double unallocatedCredit;
    private Double overdueAmount;

    private Long invoiceCount;
    private Long unpaidInvoiceCount;
    private Long overdueInvoiceCount;
    private Long paidInvoiceCount;

    private List<CustomerOutstandingInvoiceDto> outstandingInvoices;
    private List<CustomerInvoiceSummary> recentInvoices;
    private List<InvoicePayment> recentPayments;

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public Double getTotalBilled() { return totalBilled; }
    public void setTotalBilled(Double totalBilled) { this.totalBilled = totalBilled; }

    public Double getTotalPaid() { return totalPaid; }
    public void setTotalPaid(Double totalPaid) { this.totalPaid = totalPaid; }

    public Double getNetBalance() { return netBalance; }
    public void setNetBalance(Double netBalance) { this.netBalance = netBalance; }

    public Double getUnallocatedCredit() { return unallocatedCredit; }
    public void setUnallocatedCredit(Double unallocatedCredit) { this.unallocatedCredit = unallocatedCredit; }

    public Double getOverdueAmount() { return overdueAmount; }
    public void setOverdueAmount(Double overdueAmount) { this.overdueAmount = overdueAmount; }

    public Long getInvoiceCount() { return invoiceCount; }
    public void setInvoiceCount(Long invoiceCount) { this.invoiceCount = invoiceCount; }

    public Long getUnpaidInvoiceCount() { return unpaidInvoiceCount; }
    public void setUnpaidInvoiceCount(Long unpaidInvoiceCount) { this.unpaidInvoiceCount = unpaidInvoiceCount; }

    public Long getOverdueInvoiceCount() { return overdueInvoiceCount; }
    public void setOverdueInvoiceCount(Long overdueInvoiceCount) { this.overdueInvoiceCount = overdueInvoiceCount; }

    public Long getPaidInvoiceCount() { return paidInvoiceCount; }
    public void setPaidInvoiceCount(Long paidInvoiceCount) { this.paidInvoiceCount = paidInvoiceCount; }

    public List<CustomerOutstandingInvoiceDto> getOutstandingInvoices() { return outstandingInvoices; }
    public void setOutstandingInvoices(List<CustomerOutstandingInvoiceDto> outstandingInvoices) { this.outstandingInvoices = outstandingInvoices; }

    public List<CustomerInvoiceSummary> getRecentInvoices() { return recentInvoices; }
    public void setRecentInvoices(List<CustomerInvoiceSummary> recentInvoices) { this.recentInvoices = recentInvoices; }

    public List<InvoicePayment> getRecentPayments() { return recentPayments; }
    public void setRecentPayments(List<InvoicePayment> recentPayments) { this.recentPayments = recentPayments; }
}
