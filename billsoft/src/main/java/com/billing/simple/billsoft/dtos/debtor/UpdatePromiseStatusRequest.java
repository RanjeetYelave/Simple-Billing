package com.billing.simple.billsoft.dtos.debtor;

import com.billing.simple.billsoft.entities.PromiseStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePromiseStatusRequest {
    private PromiseStatus status;
    private BigDecimal fulfilledAmount;
    private LocalDate rescheduledDate;
    private String notes;
}
