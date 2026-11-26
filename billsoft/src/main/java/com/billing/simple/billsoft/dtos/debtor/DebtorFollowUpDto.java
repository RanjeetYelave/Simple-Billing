package com.billing.simple.billsoft.dtos.debtor;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtorFollowUpDto {
    private Long id;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private BigDecimal outstandingAmount;
    private BigDecimal overdueAmount;
    private LocalDate dueDate;
    private String type; // REMINDER, PROMISE_FOLLOW_UP, GENERAL
    private String title;
    private String notes;
    private String status; // UPCOMING, DUE_TODAY, OVERDUE, COMPLETED, MISSED, CANCELLED
    private String source; // "REMINDER" or "PROMISE"
    private Long sourceId;
}
