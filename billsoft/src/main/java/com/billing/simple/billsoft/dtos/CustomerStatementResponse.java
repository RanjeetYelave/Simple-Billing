package com.billing.simple.billsoft.dtos;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * Customer-specific ledger/statement with opening/closing balance.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerStatementResponse {

    private Long customerId;
    private String customerName;

    private LocalDate from;
    private LocalDate to;

    private Double openingBalance;
    private Double totalBilled;
    private Double totalPaid;
    private Double closingBalance;

    private List<StatementEntry> entries;
}
