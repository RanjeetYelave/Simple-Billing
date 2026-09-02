package com.billing.simple.billsoft.dtos;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Comprehensive Firm Statement and Executive Financial Summary.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FirmStatementResponse {

    // Period & Firm Info
    private LocalDate from;
    private LocalDate to;
    private Long firmId;
    private String firmName;
    private String firmGstin;
    private String firmPhone;
    private String firmEmail;
    private String firmAddress;

    // Sales & Revenue KPIs
    private Double totalBilled;
    private Double taxableAmount;
    private Double totalTax;
    private Double totalDiscount;
    private Integer invoiceCount;
    private Integer paidInvoicesCount;
    private Integer unpaidInvoicesCount;

    // Collections & Receivables (Inflows)
    private Double totalPaid; // Total collected from invoices / customers
    private Double outstanding; // Unpaid invoice balances / customer receivables

    // Purchases & Vendor Outflows
    private Double totalPurchases; // Total PO value
    private Integer purchaseOrderCount;
    private Double totalPaidToVendors;
    private Double outstandingPayables; // Debt owed to vendors

    // Net Financial Position
    private Double netCashflow; // Total Collections In - Total Paid to Vendors Out
    private Double netBusinessVolume; // Total Billed - Total Purchases

    // Breakdowns
    private List<GstSummaryItem> gstSummary;
    private List<PaymentModeSummary> paymentModeSummary;
    private List<FirmAccountSummary> topCustomers;
    private List<FirmAccountSummary> topVendors;

    // Unified Chronological Journal Entries
    private List<FirmJournalEntry> entries;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentModeSummary {
        private String mode;
        private Integer count;
        private Double totalAmount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FirmAccountSummary {
        private Long id;
        private String name;
        private String phone;
        private Integer transactionCount;
        private Double totalAmount;
        private Double totalPaid;
        private Double balanceDue;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FirmJournalEntry {
        private LocalDateTime date;
        private String type; // INVOICE | CUSTOMER_PAYMENT | PURCHASE_ORDER | VENDOR_PAYMENT
        private String reference;
        private String entityName; // Customer or Vendor name
        private String entityType; // CUSTOMER | VENDOR
        private String paymentMethod;
        private Double inflow; // Money received (Credits)
        private Double outflow; // Money paid out (Debits)
        private String status;
        private String notes;
    }
}
