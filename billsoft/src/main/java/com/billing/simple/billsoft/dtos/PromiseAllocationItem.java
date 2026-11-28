package com.billing.simple.billsoft.dtos;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromiseAllocationItem {
    private Long promiseId;
    private BigDecimal amount;

    public Long getPromiseId() { return promiseId; }
    public void setPromiseId(Long promiseId) { this.promiseId = promiseId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
