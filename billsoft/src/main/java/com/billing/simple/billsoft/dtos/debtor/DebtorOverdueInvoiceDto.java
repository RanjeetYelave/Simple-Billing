package com.billing.simple.billsoft.dtos.debtor;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtorOverdueInvoiceDto {
    private Long invoiceId;
    private String invoiceNumber;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal refundedAmount;
    private BigDecimal balanceDue;
    private int daysOverdue;
    private String agingBucket; // 0-30, 31-60, 61-90, 90+
}
