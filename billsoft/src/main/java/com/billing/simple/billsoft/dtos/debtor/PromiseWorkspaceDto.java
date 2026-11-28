package com.billing.simple.billsoft.dtos.debtor;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromiseWorkspaceDto {
    private int activeCount;
    private int dueTodayCount;
    private int overdueCount;
    private int fulfilledCount;
    private List<DebtorPromiseDetailDto> promises;
}
