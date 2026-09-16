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

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getDebit() { return debit; }
    public void setDebit(Double debit) { this.debit = debit; }

    public Double getCredit() { return credit; }
    public void setCredit(Double credit) { this.credit = credit; }

    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }
}

