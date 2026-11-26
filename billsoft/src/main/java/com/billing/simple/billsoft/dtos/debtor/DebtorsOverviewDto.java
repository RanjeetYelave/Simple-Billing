package com.billing.simple.billsoft.dtos.debtor;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtorsOverviewDto {
    private BigDecimal totalOutstanding;
    private BigDecimal overdueAmount;
    private BigDecimal collectedThisMonth;
    private BigDecimal advanceCredit;

    private int totalDebtorsCount;
    private int overdueDebtorsCount;
    private int criticalDebtorsCount;

    private BigDecimal aging0To30;
    private BigDecimal aging31To60;
    private BigDecimal aging61To90;
    private BigDecimal aging90Plus;

    private int todayFollowUpsCount;
    private List<DebtorFollowUpDto> todayFollowUps;
}
