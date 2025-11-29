package com.billing.simple.billsoft.dtos;

import java.time.LocalDate;
import java.util.List;

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

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }

    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }

    public Double getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(Double openingBalance) { this.openingBalance = openingBalance; }

    public Double getTotalBilled() { return totalBilled; }
    public void setTotalBilled(Double totalBilled) { this.totalBilled = totalBilled; }

    public Double getTotalPaid() { return totalPaid; }
    public void setTotalPaid(Double totalPaid) { this.totalPaid = totalPaid; }

    public Double getClosingBalance() { return closingBalance; }
    public void setClosingBalance(Double closingBalance) { this.closingBalance = closingBalance; }

    public List<StatementEntry> getEntries() { return entries; }
    public void setEntries(List<StatementEntry> entries) { this.entries = entries; }
}
