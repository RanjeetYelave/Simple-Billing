package com.billing.simple.billsoft.dtos;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;

/**
 * A single entry in customer or firm statement.
 * debit  = invoice raised (customer owes)
 * credit = customer payment
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementEntry {

    private LocalDate date;
    private String type;          // INVOICE | PAYMENT | ADJUSTMENT
    private String ref;           // invoice number or payment ref
    private String description;

    private Double debit;         // billed
    private Double credit;        // paid
    private Double balance;       // running balance after entry
}
