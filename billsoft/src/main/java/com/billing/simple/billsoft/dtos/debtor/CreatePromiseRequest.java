package com.billing.simple.billsoft.dtos.debtor;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePromiseRequest {
    private Long customerId;
    private Long invoiceId;
    private BigDecimal promisedAmount;
    private LocalDate promisedDate;
    private String notes;
}
