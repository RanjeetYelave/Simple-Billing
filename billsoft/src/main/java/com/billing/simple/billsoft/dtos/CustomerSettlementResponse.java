package com.billing.simple.billsoft.dtos;

import com.billing.simple.billsoft.entities.InvoicePayment;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSettlementResponse {
    private Long customerId;
    private BigDecimal totalAmount;
    private BigDecimal totalAllocated;
    private BigDecimal unallocatedCredit;
    private List<InvoicePayment> paymentsCreated;
    private List<CustomerOutstandingInvoiceDto> invoicesUpdated;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getTotalAllocated() { return totalAllocated; }
    public void setTotalAllocated(BigDecimal totalAllocated) { this.totalAllocated = totalAllocated; }

    public BigDecimal getUnallocatedCredit() { return unallocatedCredit; }
    public void setUnallocatedCredit(BigDecimal unallocatedCredit) { this.unallocatedCredit = unallocatedCredit; }

    public List<InvoicePayment> getPaymentsCreated() { return paymentsCreated; }
    public void setPaymentsCreated(List<InvoicePayment> paymentsCreated) { this.paymentsCreated = paymentsCreated; }

    public List<CustomerOutstandingInvoiceDto> getInvoicesUpdated() { return invoicesUpdated; }
    public void setInvoicesUpdated(List<CustomerOutstandingInvoiceDto> invoicesUpdated) { this.invoicesUpdated = invoicesUpdated; }
}
