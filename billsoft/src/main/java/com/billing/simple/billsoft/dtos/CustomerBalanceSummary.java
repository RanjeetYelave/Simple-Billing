package com.billing.simple.billsoft.dtos;

import lombok.*;
import java.math.BigDecimal;

/**
 * Authoritative Customer Financial Balance Summary.
 * Represents single-source-of-truth customer ledger calculations across all modules.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerBalanceSummary {

    private Long customerId;
    private Long firmId;

    /** Total debits from active issued invoices (FINAL, UNPAID, PAID, OVERDUE, SENT) */
    private BigDecimal totalBilled;

    /** Total credits from payments (allocated + unallocated) and legacy direct-paid fallback */
    private BigDecimal totalPaid;

    /** Total credits from sales return refund notes */
    private BigDecimal totalReturns;

    /**
     * Authoritative closing balance:
     * totalBilled - totalPaid - totalReturns.
     * Negative value represents customer store credit / advance.
     */
    private BigDecimal closingBalance;

    public Double getClosingBalanceDouble() {
        return closingBalance != null ? closingBalance.doubleValue() : 0.0;
    }

    public Double getTotalBilledDouble() {
        return totalBilled != null ? totalBilled.doubleValue() : 0.0;
    }

    public Double getTotalPaidDouble() {
        return totalPaid != null ? totalPaid.doubleValue() : 0.0;
    }

    public Double getTotalReturnsDouble() {
        return totalReturns != null ? totalReturns.doubleValue() : 0.0;
    }
}
