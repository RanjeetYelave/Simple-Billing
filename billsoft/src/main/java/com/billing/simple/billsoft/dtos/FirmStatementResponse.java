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

    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }

    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public String getFirmName() { return firmName; }
    public void setFirmName(String firmName) { this.firmName = firmName; }

    public String getFirmGstin() { return firmGstin; }
    public void setFirmGstin(String firmGstin) { this.firmGstin = firmGstin; }

    public String getFirmPhone() { return firmPhone; }
    public void setFirmPhone(String firmPhone) { this.firmPhone = firmPhone; }

    public String getFirmEmail() { return firmEmail; }
    public void setFirmEmail(String firmEmail) { this.firmEmail = firmEmail; }

    public String getFirmAddress() { return firmAddress; }
    public void setFirmAddress(String firmAddress) { this.firmAddress = firmAddress; }

    public Double getTotalBilled() { return totalBilled; }
    public void setTotalBilled(Double totalBilled) { this.totalBilled = totalBilled; }

    public Double getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(Double taxableAmount) { this.taxableAmount = taxableAmount; }

    public Double getTotalTax() { return totalTax; }
    public void setTotalTax(Double totalTax) { this.totalTax = totalTax; }

    public Double getTotalDiscount() { return totalDiscount; }
    public void setTotalDiscount(Double totalDiscount) { this.totalDiscount = totalDiscount; }

    public Integer getInvoiceCount() { return invoiceCount; }
    public void setInvoiceCount(Integer invoiceCount) { this.invoiceCount = invoiceCount; }

    public Integer getPaidInvoicesCount() { return paidInvoicesCount; }
    public void setPaidInvoicesCount(Integer paidInvoicesCount) { this.paidInvoicesCount = paidInvoicesCount; }

    public Integer getUnpaidInvoicesCount() { return unpaidInvoicesCount; }
    public void setUnpaidInvoicesCount(Integer unpaidInvoicesCount) { this.unpaidInvoicesCount = unpaidInvoicesCount; }

    public Double getTotalPaid() { return totalPaid; }
    public void setTotalPaid(Double totalPaid) { this.totalPaid = totalPaid; }

    public Double getOutstanding() { return outstanding; }
    public void setOutstanding(Double outstanding) { this.outstanding = outstanding; }

    public Double getTotalPurchases() { return totalPurchases; }
    public void setTotalPurchases(Double totalPurchases) { this.totalPurchases = totalPurchases; }

    public Integer getPurchaseOrderCount() { return purchaseOrderCount; }
    public void setPurchaseOrderCount(Integer purchaseOrderCount) { this.purchaseOrderCount = purchaseOrderCount; }

    public Double getTotalPaidToVendors() { return totalPaidToVendors; }
    public void setTotalPaidToVendors(Double totalPaidToVendors) { this.totalPaidToVendors = totalPaidToVendors; }

    public Double getOutstandingPayables() { return outstandingPayables; }
    public void setOutstandingPayables(Double outstandingPayables) { this.outstandingPayables = outstandingPayables; }

    public Double getNetCashflow() { return netCashflow; }
    public void setNetCashflow(Double netCashflow) { this.netCashflow = netCashflow; }

    public Double getNetBusinessVolume() { return netBusinessVolume; }
    public void setNetBusinessVolume(Double netBusinessVolume) { this.netBusinessVolume = netBusinessVolume; }

    public List<GstSummaryItem> getGstSummary() { return gstSummary; }
    public void setGstSummary(List<GstSummaryItem> gstSummary) { this.gstSummary = gstSummary; }

    public List<PaymentModeSummary> getPaymentModeSummary() { return paymentModeSummary; }
    public void setPaymentModeSummary(List<PaymentModeSummary> paymentModeSummary) { this.paymentModeSummary = paymentModeSummary; }

    public List<FirmAccountSummary> getTopCustomers() { return topCustomers; }
    public void setTopCustomers(List<FirmAccountSummary> topCustomers) { this.topCustomers = topCustomers; }

    public List<FirmAccountSummary> getTopVendors() { return topVendors; }
    public void setTopVendors(List<FirmAccountSummary> topVendors) { this.topVendors = topVendors; }

    public List<FirmJournalEntry> getEntries() { return entries; }
    public void setEntries(List<FirmJournalEntry> entries) { this.entries = entries; }

    public static FirmStatementResponseBuilder builder() {
        return new FirmStatementResponseBuilder();
    }

    public static class FirmStatementResponseBuilder {
        private LocalDate from;
        private LocalDate to;
        private Long firmId;
        private String firmName;
        private String firmGstin;
        private String firmPhone;
        private String firmEmail;
        private String firmAddress;
        private Double totalBilled;
        private Double taxableAmount;
        private Double totalTax;
        private Double totalDiscount;
        private Integer invoiceCount;
        private Integer paidInvoicesCount;
        private Integer unpaidInvoicesCount;
        private Double totalPaid;
        private Double outstanding;
        private Double totalPurchases;
        private Integer purchaseOrderCount;
        private Double totalPaidToVendors;
        private Double outstandingPayables;
        private Double netCashflow;
        private Double netBusinessVolume;
        private List<GstSummaryItem> gstSummary;
        private List<PaymentModeSummary> paymentModeSummary;
        private List<FirmAccountSummary> topCustomers;
        private List<FirmAccountSummary> topVendors;
        private List<FirmJournalEntry> entries;

        public FirmStatementResponseBuilder from(LocalDate from) { this.from = from; return this; }
        public FirmStatementResponseBuilder to(LocalDate to) { this.to = to; return this; }
        public FirmStatementResponseBuilder firmId(Long firmId) { this.firmId = firmId; return this; }
        public FirmStatementResponseBuilder firmName(String firmName) { this.firmName = firmName; return this; }
        public FirmStatementResponseBuilder firmGstin(String firmGstin) { this.firmGstin = firmGstin; return this; }
        public FirmStatementResponseBuilder firmPhone(String firmPhone) { this.firmPhone = firmPhone; return this; }
        public FirmStatementResponseBuilder firmEmail(String firmEmail) { this.firmEmail = firmEmail; return this; }
        public FirmStatementResponseBuilder firmAddress(String firmAddress) { this.firmAddress = firmAddress; return this; }
        public FirmStatementResponseBuilder totalBilled(Double totalBilled) { this.totalBilled = totalBilled; return this; }
        public FirmStatementResponseBuilder taxableAmount(Double taxableAmount) { this.taxableAmount = taxableAmount; return this; }
        public FirmStatementResponseBuilder totalTax(Double totalTax) { this.totalTax = totalTax; return this; }
        public FirmStatementResponseBuilder totalDiscount(Double totalDiscount) { this.totalDiscount = totalDiscount; return this; }
        public FirmStatementResponseBuilder invoiceCount(Integer invoiceCount) { this.invoiceCount = invoiceCount; return this; }
        public FirmStatementResponseBuilder paidInvoicesCount(Integer paidInvoicesCount) { this.paidInvoicesCount = paidInvoicesCount; return this; }
        public FirmStatementResponseBuilder unpaidInvoicesCount(Integer unpaidInvoicesCount) { this.unpaidInvoicesCount = unpaidInvoicesCount; return this; }
        public FirmStatementResponseBuilder totalPaid(Double totalPaid) { this.totalPaid = totalPaid; return this; }
        public FirmStatementResponseBuilder outstanding(Double outstanding) { this.outstanding = outstanding; return this; }
        public FirmStatementResponseBuilder totalPurchases(Double totalPurchases) { this.totalPurchases = totalPurchases; return this; }
        public FirmStatementResponseBuilder purchaseOrderCount(Integer purchaseOrderCount) { this.purchaseOrderCount = purchaseOrderCount; return this; }
        public FirmStatementResponseBuilder totalPaidToVendors(Double totalPaidToVendors) { this.totalPaidToVendors = totalPaidToVendors; return this; }
        public FirmStatementResponseBuilder outstandingPayables(Double outstandingPayables) { this.outstandingPayables = outstandingPayables; return this; }
        public FirmStatementResponseBuilder netCashflow(Double netCashflow) { this.netCashflow = netCashflow; return this; }
        public FirmStatementResponseBuilder netBusinessVolume(Double netBusinessVolume) { this.netBusinessVolume = netBusinessVolume; return this; }
        public FirmStatementResponseBuilder gstSummary(List<GstSummaryItem> gstSummary) { this.gstSummary = gstSummary; return this; }
        public FirmStatementResponseBuilder paymentModeSummary(List<PaymentModeSummary> paymentModeSummary) { this.paymentModeSummary = paymentModeSummary; return this; }
        public FirmStatementResponseBuilder topCustomers(List<FirmAccountSummary> topCustomers) { this.topCustomers = topCustomers; return this; }
        public FirmStatementResponseBuilder topVendors(List<FirmAccountSummary> topVendors) { this.topVendors = topVendors; return this; }
        public FirmStatementResponseBuilder entries(List<FirmJournalEntry> entries) { this.entries = entries; return this; }

        public FirmStatementResponse build() {
            FirmStatementResponse r = new FirmStatementResponse();
            r.from = this.from;
            r.to = this.to;
            r.firmId = this.firmId;
            r.firmName = this.firmName;
            r.firmGstin = this.firmGstin;
            r.firmPhone = this.firmPhone;
            r.firmEmail = this.firmEmail;
            r.firmAddress = this.firmAddress;
            r.totalBilled = this.totalBilled;
            r.taxableAmount = this.taxableAmount;
            r.totalTax = this.totalTax;
            r.totalDiscount = this.totalDiscount;
            r.invoiceCount = this.invoiceCount;
            r.paidInvoicesCount = this.paidInvoicesCount;
            r.unpaidInvoicesCount = this.unpaidInvoicesCount;
            r.totalPaid = this.totalPaid;
            r.outstanding = this.outstanding;
            r.totalPurchases = this.totalPurchases;
            r.purchaseOrderCount = this.purchaseOrderCount;
            r.totalPaidToVendors = this.totalPaidToVendors;
            r.outstandingPayables = this.outstandingPayables;
            r.netCashflow = this.netCashflow;
            r.netBusinessVolume = this.netBusinessVolume;
            r.gstSummary = this.gstSummary;
            r.paymentModeSummary = this.paymentModeSummary;
            r.topCustomers = this.topCustomers;
            r.topVendors = this.topVendors;
            r.entries = this.entries;
            return r;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentModeSummary {
        private String mode;
        private Integer count;
        private Double totalAmount;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public Double getTotalAmount() { return totalAmount; }
        public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

        public static PaymentModeSummaryBuilder builder() {
            return new PaymentModeSummaryBuilder();
        }

        public static class PaymentModeSummaryBuilder {
            private String mode;
            private Integer count;
            private Double totalAmount;

            public PaymentModeSummaryBuilder mode(String mode) { this.mode = mode; return this; }
            public PaymentModeSummaryBuilder count(Integer count) { this.count = count; return this; }
            public PaymentModeSummaryBuilder totalAmount(Double totalAmount) { this.totalAmount = totalAmount; return this; }

            public PaymentModeSummary build() {
                PaymentModeSummary s = new PaymentModeSummary();
                s.mode = this.mode;
                s.count = this.count;
                s.totalAmount = this.totalAmount;
                return s;
            }
        }
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

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public Integer getTransactionCount() { return transactionCount; }
        public void setTransactionCount(Integer transactionCount) { this.transactionCount = transactionCount; }
        public Double getTotalAmount() { return totalAmount; }
        public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
        public Double getTotalPaid() { return totalPaid; }
        public void setTotalPaid(Double totalPaid) { this.totalPaid = totalPaid; }
        public Double getBalanceDue() { return balanceDue; }
        public void setBalanceDue(Double balanceDue) { this.balanceDue = balanceDue; }

        public static FirmAccountSummaryBuilder builder() {
            return new FirmAccountSummaryBuilder();
        }

        public static class FirmAccountSummaryBuilder {
            private Long id;
            private String name;
            private String phone;
            private Integer transactionCount;
            private Double totalAmount;
            private Double totalPaid;
            private Double balanceDue;

            public FirmAccountSummaryBuilder id(Long id) { this.id = id; return this; }
            public FirmAccountSummaryBuilder name(String name) { this.name = name; return this; }
            public FirmAccountSummaryBuilder phone(String phone) { this.phone = phone; return this; }
            public FirmAccountSummaryBuilder transactionCount(Integer transactionCount) { this.transactionCount = transactionCount; return this; }
            public FirmAccountSummaryBuilder totalAmount(Double totalAmount) { this.totalAmount = totalAmount; return this; }
            public FirmAccountSummaryBuilder totalPaid(Double totalPaid) { this.totalPaid = totalPaid; return this; }
            public FirmAccountSummaryBuilder balanceDue(Double balanceDue) { this.balanceDue = balanceDue; return this; }

            public FirmAccountSummary build() {
                FirmAccountSummary s = new FirmAccountSummary();
                s.id = this.id;
                s.name = this.name;
                s.phone = this.phone;
                s.transactionCount = this.transactionCount;
                s.totalAmount = this.totalAmount;
                s.totalPaid = this.totalPaid;
                s.balanceDue = this.balanceDue;
                return s;
            }
        }
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

        public LocalDateTime getDate() { return date; }
        public void setDate(LocalDateTime date) { this.date = date; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getReference() { return reference; }
        public void setReference(String reference) { this.reference = reference; }
        public String getEntityName() { return entityName; }
        public void setEntityName(String entityName) { this.entityName = entityName; }
        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public Double getInflow() { return inflow; }
        public void setInflow(Double inflow) { this.inflow = inflow; }
        public Double getOutflow() { return outflow; }
        public void setOutflow(Double outflow) { this.outflow = outflow; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }

        public static FirmJournalEntryBuilder builder() {
            return new FirmJournalEntryBuilder();
        }

        public static class FirmJournalEntryBuilder {
            private LocalDateTime date;
            private String type;
            private String reference;
            private String entityName;
            private String entityType;
            private String paymentMethod;
            private Double inflow;
            private Double outflow;
            private String status;
            private String notes;

            public FirmJournalEntryBuilder date(LocalDateTime date) { this.date = date; return this; }
            public FirmJournalEntryBuilder type(String type) { this.type = type; return this; }
            public FirmJournalEntryBuilder reference(String reference) { this.reference = reference; return this; }
            public FirmJournalEntryBuilder entityName(String entityName) { this.entityName = entityName; return this; }
            public FirmJournalEntryBuilder entityType(String entityType) { this.entityType = entityType; return this; }
            public FirmJournalEntryBuilder paymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; return this; }
            public FirmJournalEntryBuilder inflow(Double inflow) { this.inflow = inflow; return this; }
            public FirmJournalEntryBuilder outflow(Double outflow) { this.outflow = outflow; return this; }
            public FirmJournalEntryBuilder status(String status) { this.status = status; return this; }
            public FirmJournalEntryBuilder notes(String notes) { this.notes = notes; return this; }

            public FirmJournalEntry build() {
                FirmJournalEntry e = new FirmJournalEntry();
                e.date = this.date;
                e.type = this.type;
                e.reference = this.reference;
                e.entityName = this.entityName;
                e.entityType = this.entityType;
                e.paymentMethod = this.paymentMethod;
                e.inflow = this.inflow;
                e.outflow = this.outflow;
                e.status = this.status;
                e.notes = this.notes;
                return e;
            }
        }
    }
}


