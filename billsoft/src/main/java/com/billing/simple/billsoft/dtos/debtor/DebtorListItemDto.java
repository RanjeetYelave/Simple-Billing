package com.billing.simple.billsoft.dtos.debtor;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtorListItemDto {
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String customerEmail;

    private BigDecimal outstandingAmount;
    private BigDecimal advanceCredit;
    private BigDecimal overdueAmount;

    private LocalDate oldestOverdueDate;
    private int oldestOverdueDays;

    private BigDecimal aging0To30;
    private BigDecimal aging31To60;
    private BigDecimal aging61To90;
    private BigDecimal aging90Plus;

    private LocalDate lastPaymentDate;
    private BigDecimal lastPaymentAmount;

    private LocalDate nextFollowUpDate;
    private String riskLevel; // LOW, MEDIUM, HIGH
    private String riskReason;

    private BigDecimal activePromiseAmount;
    private LocalDate activePromiseDate;
    private String promiseStatus;

    private boolean hasSupplierCounterpart;
    private BigDecimal supplierPayableAmount;
}
