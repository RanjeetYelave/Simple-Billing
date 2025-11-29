package com.billing.simple.billsoft.dtos;

import java.time.LocalDate;

public class StatementEntry {
    private LocalDate date;
    private String type; // INVOICE | PAYMENT | ADJUSTMENT
    private String ref;  // invoice number or payment ref
    private String description;
    private Double debit;   // positive = billed (customer owes)
    private Double credit;  // positive = paid (customer paid)
    private Double balance; // running balance after this entry

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
