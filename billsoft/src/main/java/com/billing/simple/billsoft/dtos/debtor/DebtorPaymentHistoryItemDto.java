package com.billing.simple.billsoft.dtos.debtor;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtorPaymentHistoryItemDto {
    private Long paymentId;
    private LocalDate paymentDate;
    private BigDecimal amount;
    private String paymentMode;
    private String referenceNumber;
    private Long invoiceId;
    private String invoiceNumber;
    private String notes;
    private boolean isAdvanceCredit;
}
