package com.billing.simple.billsoft.kpi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Customer financial KPI summary adhering to authoritative Statement/Ledger semantics.
 * Net balance = (totalBilled - totalReturns) - totalPaid.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerKpiSummary {

    private Long customerId;
    private String customerName;
    private String phone;
    private String email;
    private String gstin;
    private String address;

    private Double totalBilled;
    private Double totalPaid;
    private Double totalReturns;
    private Double netBalance; // Positive = Customer owes us (Dues), Negative = Advance

    private Long invoiceCount;
    private Long unpaidInvoiceCount;
    private Long overdueInvoiceCount;
}
