package com.billing.simple.billsoft.dtos.debtor;

import com.billing.simple.billsoft.entities.PromiseStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtorPromiseDetailDto {
    private Long id;
    private Long firmId;
    private Long customerId;
    private String customerName;
    private String customerPhone;

    private Long invoiceId;
    private String invoiceNumber;
    private BigDecimal invoiceOutstanding;

    private BigDecimal promisedAmount;
    private LocalDate promisedDate;
    private BigDecimal fulfilledAmount;
    private BigDecimal remainingAmount;

    private PromiseStatus status;
    private String displayStatus; // UPCOMING, DUE_TODAY, OVERDUE, PARTIALLY_FULFILLED, FULFILLED, CANCELLED
    private boolean isOverdue;

    private String notes;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Reconciliation flags
    private BigDecimal invoiceRemainingPromisesTotal;
    private boolean reconciliationRequired;
    private BigDecimal excessPromisedAmount;
}
