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

    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }

    public String getPartyName() { return partyName; }
    public void setPartyName(String partyName) { this.partyName = partyName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }

    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }

    public Double getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(Double openingBalance) { this.openingBalance = openingBalance; }

    public Double getTotalPurchases() { return totalPurchases; }
    public void setTotalPurchases(Double totalPurchases) { this.totalPurchases = totalPurchases; }

    public Double getTotalPaid() { return totalPaid; }
    public void setTotalPaid(Double totalPaid) { this.totalPaid = totalPaid; }

    public Double getClosingBalance() { return closingBalance; }
    public void setClosingBalance(Double closingBalance) { this.closingBalance = closingBalance; }

    public List<StatementEntry> getEntries() { return entries; }
    public void setEntries(List<StatementEntry> entries) { this.entries = entries; }

    public static PartyStatementResponseBuilder builder() {
        return new PartyStatementResponseBuilder();
    }

    public static class PartyStatementResponseBuilder {
        private Long partyId;
        private String partyName;
        private String phone;
        private String gstin;
        private String address;
        private LocalDate from;
        private LocalDate to;
        private Double openingBalance;
        private Double totalPurchases;
        private Double totalPaid;
        private Double closingBalance;
        private List<StatementEntry> entries;

        public PartyStatementResponseBuilder partyId(Long partyId) { this.partyId = partyId; return this; }
        public PartyStatementResponseBuilder partyName(String partyName) { this.partyName = partyName; return this; }
        public PartyStatementResponseBuilder phone(String phone) { this.phone = phone; return this; }
        public PartyStatementResponseBuilder gstin(String gstin) { this.gstin = gstin; return this; }
        public PartyStatementResponseBuilder address(String address) { this.address = address; return this; }
        public PartyStatementResponseBuilder from(LocalDate from) { this.from = from; return this; }
        public PartyStatementResponseBuilder to(LocalDate to) { this.to = to; return this; }
        public PartyStatementResponseBuilder openingBalance(Double openingBalance) { this.openingBalance = openingBalance; return this; }
        public PartyStatementResponseBuilder totalPurchases(Double totalPurchases) { this.totalPurchases = totalPurchases; return this; }
        public PartyStatementResponseBuilder totalPaid(Double totalPaid) { this.totalPaid = totalPaid; return this; }
        public PartyStatementResponseBuilder closingBalance(Double closingBalance) { this.closingBalance = closingBalance; return this; }
        public PartyStatementResponseBuilder entries(List<StatementEntry> entries) { this.entries = entries; return this; }

        public PartyStatementResponse build() {
            PartyStatementResponse r = new PartyStatementResponse();
            r.partyId = this.partyId;
            r.partyName = this.partyName;
            r.phone = this.phone;
            r.gstin = this.gstin;
            r.address = this.address;
            r.from = this.from;
            r.to = this.to;
            r.openingBalance = this.openingBalance;
            r.totalPurchases = this.totalPurchases;
            r.totalPaid = this.totalPaid;
            r.closingBalance = this.closingBalance;
            r.entries = this.entries;
            return r;
        }
    }
}


