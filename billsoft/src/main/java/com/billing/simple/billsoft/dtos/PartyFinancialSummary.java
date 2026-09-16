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

    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }

    public String getPartyName() { return partyName; }
    public void setPartyName(String partyName) { this.partyName = partyName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }

    public String getOpeningBalanceType() { return openingBalanceType; }
    public void setOpeningBalanceType(String openingBalanceType) { this.openingBalanceType = openingBalanceType; }

    public BigDecimal getTotalPurchases() { return totalPurchases; }
    public void setTotalPurchases(BigDecimal totalPurchases) { this.totalPurchases = totalPurchases; }

    public BigDecimal getTotalPaid() { return totalPaid; }
    public void setTotalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; }

    public BigDecimal getNetBalance() { return netBalance; }
    public void setNetBalance(BigDecimal netBalance) { this.netBalance = netBalance; }

    public String getBalanceStatus() { return balanceStatus; }
    public void setBalanceStatus(String balanceStatus) { this.balanceStatus = balanceStatus; }

    public Long getTotalPurchaseOrders() { return totalPurchaseOrders; }
    public void setTotalPurchaseOrders(Long totalPurchaseOrders) { this.totalPurchaseOrders = totalPurchaseOrders; }

    public Long getPendingPurchaseOrders() { return pendingPurchaseOrders; }
    public void setPendingPurchaseOrders(Long pendingPurchaseOrders) { this.pendingPurchaseOrders = pendingPurchaseOrders; }

    public static PartyFinancialSummaryBuilder builder() {
        return new PartyFinancialSummaryBuilder();
    }

    public static class PartyFinancialSummaryBuilder {
        private Long partyId;
        private String partyName;
        private String phone;
        private String gstin;
        private BigDecimal openingBalance;
        private String openingBalanceType;
        private BigDecimal totalPurchases;
        private BigDecimal totalPaid;
        private BigDecimal netBalance;
        private String balanceStatus;
        private Long totalPurchaseOrders;
        private Long pendingPurchaseOrders;

        public PartyFinancialSummaryBuilder partyId(Long partyId) { this.partyId = partyId; return this; }
        public PartyFinancialSummaryBuilder partyName(String partyName) { this.partyName = partyName; return this; }
        public PartyFinancialSummaryBuilder phone(String phone) { this.phone = phone; return this; }
        public PartyFinancialSummaryBuilder gstin(String gstin) { this.gstin = gstin; return this; }
        public PartyFinancialSummaryBuilder openingBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; return this; }
        public PartyFinancialSummaryBuilder openingBalanceType(String openingBalanceType) { this.openingBalanceType = openingBalanceType; return this; }
        public PartyFinancialSummaryBuilder totalPurchases(BigDecimal totalPurchases) { this.totalPurchases = totalPurchases; return this; }
        public PartyFinancialSummaryBuilder totalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; return this; }
        public PartyFinancialSummaryBuilder netBalance(BigDecimal netBalance) { this.netBalance = netBalance; return this; }
        public PartyFinancialSummaryBuilder balanceStatus(String balanceStatus) { this.balanceStatus = balanceStatus; return this; }
        public PartyFinancialSummaryBuilder totalPurchaseOrders(Long totalPurchaseOrders) { this.totalPurchaseOrders = totalPurchaseOrders; return this; }
        public PartyFinancialSummaryBuilder pendingPurchaseOrders(Long pendingPurchaseOrders) { this.pendingPurchaseOrders = pendingPurchaseOrders; return this; }

        public PartyFinancialSummary build() {
            PartyFinancialSummary s = new PartyFinancialSummary();
            s.partyId = this.partyId;
            s.partyName = this.partyName;
            s.phone = this.phone;
            s.gstin = this.gstin;
            s.openingBalance = this.openingBalance;
            s.openingBalanceType = this.openingBalanceType;
            s.totalPurchases = this.totalPurchases;
            s.totalPaid = this.totalPaid;
            s.netBalance = this.netBalance;
            s.balanceStatus = this.balanceStatus;
            s.totalPurchaseOrders = this.totalPurchaseOrders;
            s.pendingPurchaseOrders = this.pendingPurchaseOrders;
            return s;
        }
    }
}


