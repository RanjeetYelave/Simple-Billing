package com.billing.simple.billsoft.dtos;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Party (Vendor/Supplier) statement ledger response with opening balance, purchase orders, payments, and closing balance.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyStatementResponse {

    private Long partyId;
    private String partyName;
    private String phone;
    private String gstin;
    private String address;

    private LocalDate from;
    private LocalDate to;

    private Double openingBalance; // Positive = We owed them at start of period
    private Double totalPurchases;  // Total POs / Bills during period
    private Double totalPaid;       // Total Payments made during period
    private Double closingBalance;  // Closing Net Debt / Advance

    private List<StatementEntry> entries;
}
