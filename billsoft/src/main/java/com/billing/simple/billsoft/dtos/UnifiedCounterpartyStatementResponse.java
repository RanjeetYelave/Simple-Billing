package com.billing.simple.billsoft.dtos;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only combined financial overview for a linked Customer + Supplier counterparty.
 * Clearly distinguishes gross receivable and gross payable values without conflating them into a fake settlement.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnifiedCounterpartyStatementResponse {

    private Long customerId;
    private String customerName;
    private Long partyId;
    private String partyName;

    private LocalDate from;
    private LocalDate to;

    // Gross Totals
    private Double totalReceivables; // Gross amount the business owes us as a customer
    private Double totalPayables;    // Gross amount we owe the business as a supplier

    /**
     * Net position with explicit sign convention:
     * Positive (+) means the business owes us net money.
     * Negative (-) means we owe the business net money.
     * Zero (0.00) means mutually balanced.
     */
    private Double netPosition;
    private String netPositionLabel; // "NET_RECEIVABLE", "NET_PAYABLE", or "BALANCED"

    @Builder.Default
    private String disclaimer = "READ-ONLY COMBINED COUNTERPARTY VIEW (NOT A LEGAL SETTLEMENT OR ACCOUNTING VOUCHER)";

    // Combined unified chronological entries
    private List<UnifiedStatementEntry> entries;

    // Independent full underlying statement structures
    private CustomerStatementResponse customerStatement;
    private PartyStatementResponse supplierStatement;
}
