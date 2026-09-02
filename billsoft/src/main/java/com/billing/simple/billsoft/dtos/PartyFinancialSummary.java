package com.billing.simple.billsoft.dtos;

import lombok.*;

import java.math.BigDecimal;

/**
 * Summary of a Party's financial standing: Opening balance, Total purchases/orders, Total paid, and Net balance.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyFinancialSummary {
    private Long partyId;
    private String partyName;
    private String phone;
    private String gstin;
    private BigDecimal openingBalance;
    private String openingBalanceType; // "PAYABLE" or "ADVANCE"
    private BigDecimal totalPurchases;
    private BigDecimal totalPaid;
    private BigDecimal netBalance;     // Positive = We Owe (Debt/Payable), Negative = Advance (Credit)
    private String balanceStatus;      // "PAYABLE", "SETTLED", "ADVANCE"
    private Long totalPurchaseOrders;
    private Long pendingPurchaseOrders;
}
