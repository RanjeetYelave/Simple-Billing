package com.billing.simple.billsoft.dtos;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSettlementRequest {
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String paymentMode;
    private String referenceNumber;
    private String notes;
    private List<InvoiceAllocationItem> allocations;
    private Long unallocatedPaymentId; // If allocating existing advance credit

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<InvoiceAllocationItem> getAllocations() { return allocations; }
    public void setAllocations(List<InvoiceAllocationItem> allocations) { this.allocations = allocations; }

    public Long getUnallocatedPaymentId() { return unallocatedPaymentId; }
    public void setUnallocatedPaymentId(Long unallocatedPaymentId) { this.unallocatedPaymentId = unallocatedPaymentId; }
}
