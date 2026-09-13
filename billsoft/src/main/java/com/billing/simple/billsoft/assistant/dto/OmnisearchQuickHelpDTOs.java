package com.billing.simple.billsoft.assistant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative Read-Model Data Transfer Objects for OmniSearch Quick Help.
 */
public final class OmnisearchQuickHelpDTOs {

    private OmnisearchQuickHelpDTOs() {}

    public enum DataCompleteness {
        COMPLETE,
        RECENT_LIMITED,
        PARTIAL
    }

    public static class QuickHelpAction {
        private String label;
        private String icon;
        private String actionType; // NAVIGATE, VIEW, STATEMENT, PRINT, WHATSAPP
        private Map<String, Object> params = new HashMap<>();

        public QuickHelpAction() {}

        public QuickHelpAction(String label, String icon, String actionType, Map<String, Object> params) {
            this.label = label;
            this.icon = icon;
            this.actionType = actionType;
            this.params = params != null ? params : new HashMap<>();
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }
        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
    }

    public static class QuickHelpResponse<T> {
        private String status = "ANSWER"; // ANSWER | NO_MATCH | AMBIGUOUS
        private String category = "QUICK_HELP";
        private String capabilityId;
        private String title;
        private String subtitle;
        private DataCompleteness completeness = DataCompleteness.COMPLETE;
        private double confidence = 1.0;
        private T data;
        private Map<String, Object> summary = new HashMap<>();
        private List<QuickHelpAction> deepLinks = new ArrayList<>();
        private List<Map<String, Object>> candidates = new ArrayList<>();

        public QuickHelpResponse() {}

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getCapabilityId() { return capabilityId; }
        public void setCapabilityId(String capabilityId) { this.capabilityId = capabilityId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSubtitle() { return subtitle; }
        public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
        public DataCompleteness getCompleteness() { return completeness; }
        public void setCompleteness(DataCompleteness completeness) { this.completeness = completeness; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public T getData() { return data; }
        public void setData(T data) { this.data = data; }
        public Map<String, Object> getSummary() { return summary; }
        public void setSummary(Map<String, Object> summary) { this.summary = summary; }
        public List<QuickHelpAction> getDeepLinks() { return deepLinks; }
        public void setDeepLinks(List<QuickHelpAction> deepLinks) { this.deepLinks = deepLinks; }
        public List<Map<String, Object>> getCandidates() { return candidates; }
        public void setCandidates(List<Map<String, Object>> candidates) { this.candidates = candidates; }
    }

    // ─────────────────────────────────────────────────────────────
    // 1. CUSTOMER DTO
    // ─────────────────────────────────────────────────────────────
    public static class CustomerQuickHelpDTO {
        private Long id;
        private String name;
        private String phone;
        private String email;
        private String address;
        private String gstin;
        private BigDecimal totalSales = BigDecimal.ZERO;
        private BigDecimal totalPaid = BigDecimal.ZERO;
        private BigDecimal totalReturnCredit = BigDecimal.ZERO;
        private BigDecimal currentOutstanding = BigDecimal.ZERO;
        private long totalInvoiceCount;
        private long unpaidInvoiceCount;
        private long overdueInvoiceCount;
        private LocalDate lastTransactionDate;
        private BigDecimal lastPaymentAmount;
        private LocalDate lastPaymentDate;
        private List<InvoiceSummaryDTO> recentInvoices = new ArrayList<>();

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getGstin() { return gstin; }
        public void setGstin(String gstin) { this.gstin = gstin; }
        public BigDecimal getTotalSales() { return totalSales; }
        public void setTotalSales(BigDecimal totalSales) { this.totalSales = totalSales; }
        public BigDecimal getTotalPaid() { return totalPaid; }
        public void setTotalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; }
        public BigDecimal getTotalReturnCredit() { return totalReturnCredit; }
        public void setTotalReturnCredit(BigDecimal totalReturnCredit) { this.totalReturnCredit = totalReturnCredit; }
        public BigDecimal getCurrentOutstanding() { return currentOutstanding; }
        public void setCurrentOutstanding(BigDecimal currentOutstanding) { this.currentOutstanding = currentOutstanding; }
        public long getTotalInvoiceCount() { return totalInvoiceCount; }
        public void setTotalInvoiceCount(long totalInvoiceCount) { this.totalInvoiceCount = totalInvoiceCount; }
        public long getUnpaidInvoiceCount() { return unpaidInvoiceCount; }
        public void setUnpaidInvoiceCount(long unpaidInvoiceCount) { this.unpaidInvoiceCount = unpaidInvoiceCount; }
        public long getOverdueInvoiceCount() { return overdueInvoiceCount; }
        public void setOverdueInvoiceCount(long overdueInvoiceCount) { this.overdueInvoiceCount = overdueInvoiceCount; }
        public LocalDate getLastTransactionDate() { return lastTransactionDate; }
        public void setLastTransactionDate(LocalDate lastTransactionDate) { this.lastTransactionDate = lastTransactionDate; }
        public BigDecimal getLastPaymentAmount() { return lastPaymentAmount; }
        public void setLastPaymentAmount(BigDecimal lastPaymentAmount) { this.lastPaymentAmount = lastPaymentAmount; }
        public LocalDate getLastPaymentDate() { return lastPaymentDate; }
        public void setLastPaymentDate(LocalDate lastPaymentDate) { this.lastPaymentDate = lastPaymentDate; }
        public List<InvoiceSummaryDTO> getRecentInvoices() { return recentInvoices; }
        public void setRecentInvoices(List<InvoiceSummaryDTO> recentInvoices) { this.recentInvoices = recentInvoices; }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. VENDOR / PARTY DTO
    // ─────────────────────────────────────────────────────────────
    public static class VendorQuickHelpDTO {
        private Long id;
        private String name;
        private String contactPerson;
        private String phone;
        private String email;
        private String address;
        private String gstin;
        private String pan;
        private String bankName;
        private String bankAccount;
        private String bankIfsc;
        private String upiId;
        private BigDecimal openingBalance = BigDecimal.ZERO;
        private String openingBalanceType;
        private BigDecimal totalPurchases = BigDecimal.ZERO;
        private BigDecimal totalPaid = BigDecimal.ZERO;
        private BigDecimal currentPayable = BigDecimal.ZERO;
        private long totalPurchaseOrderCount;
        private List<PurchaseOrderSummaryDTO> recentPurchaseOrders = new ArrayList<>();

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getContactPerson() { return contactPerson; }
        public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getGstin() { return gstin; }
        public void setGstin(String gstin) { this.gstin = gstin; }
        public String getPan() { return pan; }
        public void setPan(String pan) { this.pan = pan; }
        public String getBankName() { return bankName; }
        public void setBankName(String bankName) { this.bankName = bankName; }
        public String getBankAccount() { return bankAccount; }
        public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }
        public String getBankIfsc() { return bankIfsc; }
        public void setBankIfsc(String bankIfsc) { this.bankIfsc = bankIfsc; }
        public String getUpiId() { return upiId; }
        public void setUpiId(String upiId) { this.upiId = upiId; }
        public BigDecimal getOpeningBalance() { return openingBalance; }
        public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }
        public String getOpeningBalanceType() { return openingBalanceType; }
        public void setOpeningBalanceType(String openingBalanceType) { this.openingBalanceType = openingBalanceType; }
        public BigDecimal getTotalPurchases() { return totalPurchases; }
        public void setTotalPurchases(BigDecimal totalPurchases) { this.totalPurchases = totalPurchases; }
        public BigDecimal getTotalPaid() { return totalPaid; }
        public void setTotalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; }
        public BigDecimal getCurrentPayable() { return currentPayable; }
        public void setCurrentPayable(BigDecimal currentPayable) { this.currentPayable = currentPayable; }
        public long getTotalPurchaseOrderCount() { return totalPurchaseOrderCount; }
        public void setTotalPurchaseOrderCount(long totalPurchaseOrderCount) { this.totalPurchaseOrderCount = totalPurchaseOrderCount; }
        public List<PurchaseOrderSummaryDTO> getRecentPurchaseOrders() { return recentPurchaseOrders; }
        public void setRecentPurchaseOrders(List<PurchaseOrderSummaryDTO> recentPurchaseOrders) { this.recentPurchaseOrders = recentPurchaseOrders; }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. INVOICE & ESTIMATE DTOs
    // ─────────────────────────────────────────────────────────────
    public static class InvoiceSummaryDTO {
        private Long id;
        private String invoiceNumber;
        private String estimateNumber;
        private String customerName;
        private Long customerId;
        private LocalDateTime invoiceDate;
        private LocalDate dueDate;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private BigDecimal paidAmount = BigDecimal.ZERO;
        private BigDecimal balanceAmount = BigDecimal.ZERO;
        private String status;
        private boolean isOverdue;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getInvoiceNumber() { return invoiceNumber; }
        public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
        public String getEstimateNumber() { return estimateNumber; }
        public void setEstimateNumber(String estimateNumber) { this.estimateNumber = estimateNumber; }
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public Long getCustomerId() { return customerId; }
        public void setCustomerId(Long customerId) { this.customerId = customerId; }
        public LocalDateTime getInvoiceDate() { return invoiceDate; }
        public void setInvoiceDate(LocalDateTime invoiceDate) { this.invoiceDate = invoiceDate; }
        public LocalDate getDueDate() { return dueDate; }
        public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public BigDecimal getPaidAmount() { return paidAmount; }
        public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
        public BigDecimal getBalanceAmount() { return balanceAmount; }
        public void setBalanceAmount(BigDecimal balanceAmount) { this.balanceAmount = balanceAmount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public boolean isOverdue() { return isOverdue; }
        public void setOverdue(boolean overdue) { isOverdue = overdue; }
    }

    public static class InvoiceDetailDTO extends InvoiceSummaryDTO {
        private BigDecimal discount = BigDecimal.ZERO;
        private BigDecimal taxAmount = BigDecimal.ZERO;
        private String paymentType;
        private List<InvoiceItemDTO> items = new ArrayList<>();
        private List<PaymentRecordDTO> payments = new ArrayList<>();
        private List<SalesReturnSummaryDTO> returns = new ArrayList<>();

        public BigDecimal getDiscount() { return discount; }
        public void setDiscount(BigDecimal discount) { this.discount = discount; }
        public BigDecimal getTaxAmount() { return taxAmount; }
        public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
        public String getPaymentType() { return paymentType; }
        public void setPaymentType(String paymentType) { this.paymentType = paymentType; }
        public List<InvoiceItemDTO> getItems() { return items; }
        public void setItems(List<InvoiceItemDTO> items) { this.items = items; }
        public List<PaymentRecordDTO> getPayments() { return payments; }
        public void setPayments(List<PaymentRecordDTO> payments) { this.payments = payments; }
        public List<SalesReturnSummaryDTO> getReturns() { return returns; }
        public void setReturns(List<SalesReturnSummaryDTO> returns) { this.returns = returns; }
    }

    public static class InvoiceItemDTO {
        private Long id;
        private String productName;
        private String description;
        private double quantity;
        private BigDecimal price = BigDecimal.ZERO;
        private BigDecimal taxRate = BigDecimal.ZERO;
        private BigDecimal taxAmount = BigDecimal.ZERO;
        private BigDecimal totalAmount = BigDecimal.ZERO;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public double getQuantity() { return quantity; }
        public void setQuantity(double quantity) { this.quantity = quantity; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public BigDecimal getTaxRate() { return taxRate; }
        public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
        public BigDecimal getTaxAmount() { return taxAmount; }
        public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    }

    public static class PaymentRecordDTO {
        private Long id;
        private BigDecimal amount = BigDecimal.ZERO;
        private LocalDate paymentDate;
        private String paymentMode;
        private String referenceNumber;
        private String notes;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public LocalDate getPaymentDate() { return paymentDate; }
        public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
        public String getPaymentMode() { return paymentMode; }
        public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }
        public String getReferenceNumber() { return referenceNumber; }
        public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class SalesReturnSummaryDTO {
        private Long id;
        private String returnNumber;
        private LocalDate returnDate;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private String reason;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getReturnNumber() { return returnNumber; }
        public void setReturnNumber(String returnNumber) { this.returnNumber = returnNumber; }
        public LocalDate getReturnDate() { return returnDate; }
        public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class PurchaseOrderSummaryDTO {
        private Long id;
        private String orderNumber;
        private String partyName;
        private LocalDate orderDate;
        private String status;
        private BigDecimal totalAmount = BigDecimal.ZERO;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getOrderNumber() { return orderNumber; }
        public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
        public String getPartyName() { return partyName; }
        public void setPartyName(String partyName) { this.partyName = partyName; }
        public LocalDate getOrderDate() { return orderDate; }
        public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. SALES & EXPENSES SUMMARY DTOs
    // ─────────────────────────────────────────────────────────────
    public static class SalesSummaryDTO {
        private String dateRangeLabel;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private BigDecimal totalRevenue = BigDecimal.ZERO;
        private BigDecimal totalTaxCollected = BigDecimal.ZERO;
        private BigDecimal totalPaidReceived = BigDecimal.ZERO;
        private BigDecimal totalPendingBalance = BigDecimal.ZERO;
        private long invoiceCount;
        private long paidInvoiceCount;
        private long unpaidInvoiceCount;
        private BigDecimal averageTicketSize = BigDecimal.ZERO;
        private List<Map<String, Object>> topCustomers = new ArrayList<>();
        private List<InvoiceSummaryDTO> recentInvoices = new ArrayList<>();

        public String getDateRangeLabel() { return dateRangeLabel; }
        public void setDateRangeLabel(String dateRangeLabel) { this.dateRangeLabel = dateRangeLabel; }
        public LocalDateTime getFromDate() { return fromDate; }
        public void setFromDate(LocalDateTime fromDate) { this.fromDate = fromDate; }
        public LocalDateTime getToDate() { return toDate; }
        public void setToDate(LocalDateTime toDate) { this.toDate = toDate; }
        public BigDecimal getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
        public BigDecimal getTotalTaxCollected() { return totalTaxCollected; }
        public void setTotalTaxCollected(BigDecimal totalTaxCollected) { this.totalTaxCollected = totalTaxCollected; }
        public BigDecimal getTotalPaidReceived() { return totalPaidReceived; }
        public void setTotalPaidReceived(BigDecimal totalPaidReceived) { this.totalPaidReceived = totalPaidReceived; }
        public BigDecimal getTotalPendingBalance() { return totalPendingBalance; }
        public void setTotalPendingBalance(BigDecimal totalPendingBalance) { this.totalPendingBalance = totalPendingBalance; }
        public long getInvoiceCount() { return invoiceCount; }
        public void setInvoiceCount(long invoiceCount) { this.invoiceCount = invoiceCount; }
        public long getPaidInvoiceCount() { return paidInvoiceCount; }
        public void setPaidInvoiceCount(long paidInvoiceCount) { this.paidInvoiceCount = paidInvoiceCount; }
        public long getUnpaidInvoiceCount() { return unpaidInvoiceCount; }
        public void setUnpaidInvoiceCount(long unpaidInvoiceCount) { this.unpaidInvoiceCount = unpaidInvoiceCount; }
        public BigDecimal getAverageTicketSize() { return averageTicketSize; }
        public void setAverageTicketSize(BigDecimal averageTicketSize) { this.averageTicketSize = averageTicketSize; }
        public List<Map<String, Object>> getTopCustomers() { return topCustomers; }
        public void setTopCustomers(List<Map<String, Object>> topCustomers) { this.topCustomers = topCustomers; }
        public List<InvoiceSummaryDTO> getRecentInvoices() { return recentInvoices; }
        public void setRecentInvoices(List<InvoiceSummaryDTO> recentInvoices) { this.recentInvoices = recentInvoices; }
    }

    public static class ExpenseSummaryDTO {
        private String dateRangeLabel;
        private LocalDate fromDate;
        private LocalDate toDate;
        private BigDecimal totalExpense = BigDecimal.ZERO;
        private long expenseCount;
        private Map<String, BigDecimal> categoryBreakdown = new HashMap<>();
        private Map<String, BigDecimal> paymentModeBreakdown = new HashMap<>();
        private List<ExpenseItemDTO> recentExpenses = new ArrayList<>();

        public String getDateRangeLabel() { return dateRangeLabel; }
        public void setDateRangeLabel(String dateRangeLabel) { this.dateRangeLabel = dateRangeLabel; }
        public LocalDate getFromDate() { return fromDate; }
        public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
        public LocalDate getToDate() { return toDate; }
        public void setToDate(LocalDate toDate) { this.toDate = toDate; }
        public BigDecimal getTotalExpense() { return totalExpense; }
        public void setTotalExpense(BigDecimal totalExpense) { this.totalExpense = totalExpense; }
        public long getExpenseCount() { return expenseCount; }
        public void setExpenseCount(long expenseCount) { this.expenseCount = expenseCount; }
        public Map<String, BigDecimal> getCategoryBreakdown() { return categoryBreakdown; }
        public void setCategoryBreakdown(Map<String, BigDecimal> categoryBreakdown) { this.categoryBreakdown = categoryBreakdown; }
        public Map<String, BigDecimal> getPaymentModeBreakdown() { return paymentModeBreakdown; }
        public void setPaymentModeBreakdown(Map<String, BigDecimal> paymentModeBreakdown) { this.paymentModeBreakdown = paymentModeBreakdown; }
        public List<ExpenseItemDTO> getRecentExpenses() { return recentExpenses; }
        public void setRecentExpenses(List<ExpenseItemDTO> recentExpenses) { this.recentExpenses = recentExpenses; }
    }

    public static class ExpenseItemDTO {
        private Long id;
        private String title;
        private BigDecimal amount = BigDecimal.ZERO;
        private String category;
        private LocalDate expenseDate;
        private String paymentMode;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public LocalDate getExpenseDate() { return expenseDate; }
        public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }
        public String getPaymentMode() { return paymentMode; }
        public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. INVENTORY & PRODUCT DTO
    // ─────────────────────────────────────────────────────────────
    public static class InventorySummaryDTO {
        private long totalProductsCount;
        private long lowStockCount;
        private long outOfStockCount;
        private BigDecimal totalRetailValuation = BigDecimal.ZERO;
        private BigDecimal totalCostValuation = BigDecimal.ZERO;
        private List<ProductSummaryDTO> lowStockItems = new ArrayList<>();
        private List<ProductSummaryDTO> matchedProducts = new ArrayList<>();

        public long getTotalProductsCount() { return totalProductsCount; }
        public void setTotalProductsCount(long totalProductsCount) { this.totalProductsCount = totalProductsCount; }
        public long getLowStockCount() { return lowStockCount; }
        public void setLowStockCount(long lowStockCount) { this.lowStockCount = lowStockCount; }
        public long getOutOfStockCount() { return outOfStockCount; }
        public void setOutOfStockCount(long outOfStockCount) { this.outOfStockCount = outOfStockCount; }
        public BigDecimal getTotalRetailValuation() { return totalRetailValuation; }
        public void setTotalRetailValuation(BigDecimal totalRetailValuation) { this.totalRetailValuation = totalRetailValuation; }
        public BigDecimal getTotalCostValuation() { return totalCostValuation; }
        public void setTotalCostValuation(BigDecimal totalCostValuation) { this.totalCostValuation = totalCostValuation; }
        public List<ProductSummaryDTO> getLowStockItems() { return lowStockItems; }
        public void setLowStockItems(List<ProductSummaryDTO> lowStockItems) { this.lowStockItems = lowStockItems; }
        public List<ProductSummaryDTO> getMatchedProducts() { return matchedProducts; }
        public void setMatchedProducts(List<ProductSummaryDTO> matchedProducts) { this.matchedProducts = matchedProducts; }
    }

    public static class ProductSummaryDTO {
        private Long id;
        private String name;
        private BigDecimal price = BigDecimal.ZERO;
        private BigDecimal costPrice = BigDecimal.ZERO;
        private BigDecimal stockQuantity = BigDecimal.ZERO;
        private BigDecimal minStockLevel = BigDecimal.ZERO;
        private String sku;
        private String barcode;
        private String category;
        private String unit;
        private BigDecimal gstPercentage = BigDecimal.ZERO;
        private boolean isLowStock;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public BigDecimal getCostPrice() { return costPrice; }
        public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }
        public BigDecimal getStockQuantity() { return stockQuantity; }
        public void setStockQuantity(BigDecimal stockQuantity) { this.stockQuantity = stockQuantity; }
        public BigDecimal getMinStockLevel() { return minStockLevel; }
        public void setMinStockLevel(BigDecimal minStockLevel) { this.minStockLevel = minStockLevel; }
        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public String getBarcode() { return barcode; }
        public void setBarcode(String barcode) { this.barcode = barcode; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public BigDecimal getGstPercentage() { return gstPercentage; }
        public void setGstPercentage(BigDecimal gstPercentage) { this.gstPercentage = gstPercentage; }
        public boolean isLowStock() { return isLowStock; }
        public void setLowStock(boolean lowStock) { isLowStock = lowStock; }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. HR & EMPLOYEE DTO
    // ─────────────────────────────────────────────────────────────
    public static class HrStaffSummaryDTO {
        private Long id;
        private String name;
        private String phone;
        private String role;
        private String department;
        private String designation;
        private String email;
        private LocalDate dateOfJoining;
        private Boolean isActive = true;
        private Double monthlyBaseSalary = 0.0;
        private Double currentAdvanceBalance = 0.0;
        private Integer allowedPaidLeavesPerMonth = 0;
        private String todayAttendanceStatus; // PRESENT, ABSENT, HALF_DAY, LEAVE, NOT_MARKED
        private long presentDaysThisMonth;
        private long absentDaysThisMonth;
        private long halfDaysThisMonth;
        private long leaveDaysThisMonth;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
        public String getDesignation() { return designation; }
        public void setDesignation(String designation) { this.designation = designation; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public LocalDate getDateOfJoining() { return dateOfJoining; }
        public void setDateOfJoining(LocalDate dateOfJoining) { this.dateOfJoining = dateOfJoining; }
        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
        public Double getMonthlyBaseSalary() { return monthlyBaseSalary; }
        public void setMonthlyBaseSalary(Double monthlyBaseSalary) { this.monthlyBaseSalary = monthlyBaseSalary; }
        public Double getCurrentAdvanceBalance() { return currentAdvanceBalance; }
        public void setCurrentAdvanceBalance(Double currentAdvanceBalance) { this.currentAdvanceBalance = currentAdvanceBalance; }
        public Integer getAllowedPaidLeavesPerMonth() { return allowedPaidLeavesPerMonth; }
        public void setAllowedPaidLeavesPerMonth(Integer allowedPaidLeavesPerMonth) { this.allowedPaidLeavesPerMonth = allowedPaidLeavesPerMonth; }
        public String getTodayAttendanceStatus() { return todayAttendanceStatus; }
        public void setTodayAttendanceStatus(String todayAttendanceStatus) { this.todayAttendanceStatus = todayAttendanceStatus; }
        public long getPresentDaysThisMonth() { return presentDaysThisMonth; }
        public void setPresentDaysThisMonth(long presentDaysThisMonth) { this.presentDaysThisMonth = presentDaysThisMonth; }
        public long getAbsentDaysThisMonth() { return absentDaysThisMonth; }
        public void setAbsentDaysThisMonth(long absentDaysThisMonth) { this.absentDaysThisMonth = absentDaysThisMonth; }
        public long getHalfDaysThisMonth() { return halfDaysThisMonth; }
        public void setHalfDaysThisMonth(long halfDaysThisMonth) { this.halfDaysThisMonth = halfDaysThisMonth; }
        public long getLeaveDaysThisMonth() { return leaveDaysThisMonth; }
        public void setLeaveDaysThisMonth(long leaveDaysThisMonth) { this.leaveDaysThisMonth = leaveDaysThisMonth; }
    }

    // ─────────────────────────────────────────────────────────────
    // 7. MACRO CROSS-DOMAIN SUMMARY DTO
    // ─────────────────────────────────────────────────────────────
    public static class MacroBusinessSummaryDTO {
        private BigDecimal totalReceivables = BigDecimal.ZERO; // Market Udhari
        private BigDecimal totalPayables = BigDecimal.ZERO;    // Vendor Dues
        private long totalCustomerCount;
        private long totalVendorCount;
        private long debtorsCount;
        private long creditorsCount;
        private List<Map<String, Object>> topDebtors = new ArrayList<>();
        private List<Map<String, Object>> topCreditors = new ArrayList<>();

        public BigDecimal getTotalReceivables() { return totalReceivables; }
        public void setTotalReceivables(BigDecimal totalReceivables) { this.totalReceivables = totalReceivables; }
        public BigDecimal getTotalPayables() { return totalPayables; }
        public void setTotalPayables(BigDecimal totalPayables) { this.totalPayables = totalPayables; }
        public long getTotalCustomerCount() { return totalCustomerCount; }
        public void setTotalCustomerCount(long totalCustomerCount) { this.totalCustomerCount = totalCustomerCount; }
        public long getTotalVendorCount() { return totalVendorCount; }
        public void setTotalVendorCount(long totalVendorCount) { this.totalVendorCount = totalVendorCount; }
        public long getDebtorsCount() { return debtorsCount; }
        public void setDebtorsCount(long debtorsCount) { this.debtorsCount = debtorsCount; }
        public long getCreditorsCount() { return creditorsCount; }
        public void setCreditorsCount(long creditorsCount) { this.creditorsCount = creditorsCount; }
        public List<Map<String, Object>> getTopDebtors() { return topDebtors; }
        public void setTopDebtors(List<Map<String, Object>> topDebtors) { this.topDebtors = topDebtors; }
        public List<Map<String, Object>> getTopCreditors() { return topCreditors; }
        public void setTopCreditors(List<Map<String, Object>> topCreditors) { this.topCreditors = topCreditors; }
    }

    // ─────────────────────────────────────────────────────────────
    // 8. SALARY & PAYROLL DTO
    // ─────────────────────────────────────────────────────────────
    public static class SalaryQuickHelpDTO {
        private String period;
        private Double totalPayroll = 0.0;
        private long totalPaidCount;
        private Double totalAdvanceDeductions = 0.0;
        private Double totalBonuses = 0.0;
        private String employeeName;
        private Double employeeSalary = 0.0;
        private List<Map<String, Object>> salaryRecords = new ArrayList<>();

        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        public Double getTotalPayroll() { return totalPayroll; }
        public void setTotalPayroll(Double totalPayroll) { this.totalPayroll = totalPayroll; }
        public long getTotalPaidCount() { return totalPaidCount; }
        public void setTotalPaidCount(long totalPaidCount) { this.totalPaidCount = totalPaidCount; }
        public Double getTotalAdvanceDeductions() { return totalAdvanceDeductions; }
        public void setTotalAdvanceDeductions(Double totalAdvanceDeductions) { this.totalAdvanceDeductions = totalAdvanceDeductions; }
        public Double getTotalBonuses() { return totalBonuses; }
        public void setTotalBonuses(Double totalBonuses) { this.totalBonuses = totalBonuses; }
        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
        public Double getEmployeeSalary() { return employeeSalary; }
        public void setEmployeeSalary(Double employeeSalary) { this.employeeSalary = employeeSalary; }
        public List<Map<String, Object>> getSalaryRecords() { return salaryRecords; }
        public void setSalaryRecords(List<Map<String, Object>> salaryRecords) { this.salaryRecords = salaryRecords; }
    }

    // ─────────────────────────────────────────────────────────────
    // 9. STAFF ADVANCE DTO
    // ─────────────────────────────────────────────────────────────
    public static class AdvanceQuickHelpDTO {
        private Double totalOutstandingAdvances = 0.0;
        private long staffWithAdvancesCount;
        private String employeeName;
        private Double employeeAdvanceBalance = 0.0;
        private List<Map<String, Object>> advanceRecords = new ArrayList<>();

        public Double getTotalOutstandingAdvances() { return totalOutstandingAdvances; }
        public void setTotalOutstandingAdvances(Double totalOutstandingAdvances) { this.totalOutstandingAdvances = totalOutstandingAdvances; }
        public long getStaffWithAdvancesCount() { return staffWithAdvancesCount; }
        public void setStaffWithAdvancesCount(long staffWithAdvancesCount) { this.staffWithAdvancesCount = staffWithAdvancesCount; }
        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
        public Double getEmployeeAdvanceBalance() { return employeeAdvanceBalance; }
        public void setEmployeeAdvanceBalance(Double employeeAdvanceBalance) { this.employeeAdvanceBalance = employeeAdvanceBalance; }
        public List<Map<String, Object>> getAdvanceRecords() { return advanceRecords; }
        public void setAdvanceRecords(List<Map<String, Object>> advanceRecords) { this.advanceRecords = advanceRecords; }
    }

    // ─────────────────────────────────────────────────────────────
    // 10. SALES RETURNS & CREDIT NOTES DTO
    // ─────────────────────────────────────────────────────────────
    public static class SalesReturnQuickHelpDTO {
        private String period;
        private BigDecimal totalReturnValue = BigDecimal.ZERO;
        private long returnCount;
        private List<Map<String, Object>> returnRecords = new ArrayList<>();

        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        public BigDecimal getTotalReturnValue() { return totalReturnValue; }
        public void setTotalReturnValue(BigDecimal totalReturnValue) { this.totalReturnValue = totalReturnValue; }
        public long getReturnCount() { return returnCount; }
        public void setReturnCount(long returnCount) { this.returnCount = returnCount; }
        public List<Map<String, Object>> getReturnRecords() { return returnRecords; }
        public void setReturnRecords(List<Map<String, Object>> returnRecords) { this.returnRecords = returnRecords; }
    }

    // ─────────────────────────────────────────────────────────────
    // 11. COLLECTIONS & RECEIPTS DTO
    // ─────────────────────────────────────────────────────────────
    public static class CollectionReceiptQuickHelpDTO {
        private String period;
        private BigDecimal totalCollection = BigDecimal.ZERO;
        private long receiptCount;
        private Map<String, BigDecimal> modeBreakdown = new HashMap<>();
        private List<Map<String, Object>> receipts = new ArrayList<>();

        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        public BigDecimal getTotalCollection() { return totalCollection; }
        public void setTotalCollection(BigDecimal totalCollection) { this.totalCollection = totalCollection; }
        public long getReceiptCount() { return receiptCount; }
        public void setReceiptCount(long receiptCount) { this.receiptCount = receiptCount; }
        public Map<String, BigDecimal> getModeBreakdown() { return modeBreakdown; }
        public void setModeBreakdown(Map<String, BigDecimal> modeBreakdown) { this.modeBreakdown = modeBreakdown; }
        public List<Map<String, Object>> getReceipts() { return receipts; }
        public void setReceipts(List<Map<String, Object>> receipts) { this.receipts = receipts; }
    }

    // ─────────────────────────────────────────────────────────────
    // 12. VENDOR PAYMENTS DTO
    // ─────────────────────────────────────────────────────────────
    public static class VendorPayoutQuickHelpDTO {
        private String period;
        private BigDecimal totalPayout = BigDecimal.ZERO;
        private long paymentCount;
        private String vendorName;
        private List<Map<String, Object>> paymentRecords = new ArrayList<>();

        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        public BigDecimal getTotalPayout() { return totalPayout; }
        public void setTotalPayout(BigDecimal totalPayout) { this.totalPayout = totalPayout; }
        public long getPaymentCount() { return paymentCount; }
        public void setPaymentCount(long paymentCount) { this.paymentCount = paymentCount; }
        public String getVendorName() { return vendorName; }
        public void setVendorName(String vendorName) { this.vendorName = vendorName; }
        public List<Map<String, Object>> getPaymentRecords() { return paymentRecords; }
        public void setPaymentRecords(List<Map<String, Object>> paymentRecords) { this.paymentRecords = paymentRecords; }
    }

    // ─────────────────────────────────────────────────────────────
    // 13. PURCHASE ORDERS DTO
    // ─────────────────────────────────────────────────────────────
    public static class PurchaseOrderQuickHelpDTO {
        private long totalPoCount;
        private long openPoCount;
        private BigDecimal totalPoAmount = BigDecimal.ZERO;
        private List<Map<String, Object>> recentOrders = new ArrayList<>();

        public long getTotalPoCount() { return totalPoCount; }
        public void setTotalPoCount(long totalPoCount) { this.totalPoCount = totalPoCount; }
        public long getOpenPoCount() { return openPoCount; }
        public void setOpenPoCount(long openPoCount) { this.openPoCount = openPoCount; }
        public BigDecimal getTotalPoAmount() { return totalPoAmount; }
        public void setTotalPoAmount(BigDecimal totalPoAmount) { this.totalPoAmount = totalPoAmount; }
        public List<Map<String, Object>> getRecentOrders() { return recentOrders; }
        public void setRecentOrders(List<Map<String, Object>> recentOrders) { this.recentOrders = recentOrders; }
    }

    // ─────────────────────────────────────────────────────────────
    // 14. STOCK MOVEMENTS DTO
    // ─────────────────────────────────────────────────────────────
    public static class StockMovementQuickHelpDTO {
        private String productName;
        private long totalMovements;
        private BigDecimal totalInward = BigDecimal.ZERO;
        private BigDecimal totalOutward = BigDecimal.ZERO;
        private List<Map<String, Object>> movements = new ArrayList<>();

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public long getTotalMovements() { return totalMovements; }
        public void setTotalMovements(long totalMovements) { this.totalMovements = totalMovements; }
        public BigDecimal getTotalInward() { return totalInward; }
        public void setTotalInward(BigDecimal totalInward) { this.totalInward = totalInward; }
        public BigDecimal getTotalOutward() { return totalOutward; }
        public void setTotalOutward(BigDecimal totalOutward) { this.totalOutward = totalOutward; }
        public List<Map<String, Object>> getMovements() { return movements; }
        public void setMovements(List<Map<String, Object>> movements) { this.movements = movements; }
    }

    // ─────────────────────────────────────────────────────────────
    // 15. LEDGER / STATEMENT DTO
    // ─────────────────────────────────────────────────────────────
    public static class LedgerStatementQuickHelpDTO {
        private String entityType; // CUSTOMER, VENDOR
        private Long entityId;
        private String entityName;
        private BigDecimal openingBalance = BigDecimal.ZERO;
        private BigDecimal totalDebits = BigDecimal.ZERO;
        private BigDecimal totalCredits = BigDecimal.ZERO;
        private BigDecimal closingBalance = BigDecimal.ZERO;
        private List<Map<String, Object>> transactions = new ArrayList<>();

        public String getEntityType() { return entityType; }
        public void setEntityType(String entityType) { this.entityType = entityType; }
        public Long getEntityId() { return entityId; }
        public void setEntityId(Long entityId) { this.entityId = entityId; }
        public String getEntityName() { return entityName; }
        public void setEntityName(String entityName) { this.entityName = entityName; }
        public BigDecimal getOpeningBalance() { return openingBalance; }
        public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }
        public BigDecimal getTotalDebits() { return totalDebits; }
        public void setTotalDebits(BigDecimal totalDebits) { this.totalDebits = totalDebits; }
        public BigDecimal getTotalCredits() { return totalCredits; }
        public void setTotalCredits(BigDecimal totalCredits) { this.totalCredits = totalCredits; }
        public BigDecimal getClosingBalance() { return closingBalance; }
        public void setClosingBalance(BigDecimal closingBalance) { this.closingBalance = closingBalance; }
        public List<Map<String, Object>> getTransactions() { return transactions; }
        public void setTransactions(List<Map<String, Object>> transactions) { this.transactions = transactions; }
    }

    // ─────────────────────────────────────────────────────────────
    // 16. UNIFIED SEARCH RESULT DTO
    // ─────────────────────────────────────────────────────────────
    public static class UnifiedSearchResultDTO {
        private String query;
        private List<Map<String, Object>> customers = new ArrayList<>();
        private List<Map<String, Object>> employees = new ArrayList<>();
        private List<Map<String, Object>> vendors = new ArrayList<>();
        private List<Map<String, Object>> products = new ArrayList<>();
        private List<Map<String, Object>> invoices = new ArrayList<>();
        private List<Map<String, Object>> purchaseOrders = new ArrayList<>();
        private List<Map<String, Object>> expenses = new ArrayList<>();

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public List<Map<String, Object>> getCustomers() { return customers; }
        public void setCustomers(List<Map<String, Object>> customers) { this.customers = customers; }
        public List<Map<String, Object>> getEmployees() { return employees; }
        public void setEmployees(List<Map<String, Object>> employees) { this.employees = employees; }
        public List<Map<String, Object>> getVendors() { return vendors; }
        public void setVendors(List<Map<String, Object>> vendors) { this.vendors = vendors; }
        public List<Map<String, Object>> getProducts() { return products; }
        public void setProducts(List<Map<String, Object>> products) { this.products = products; }
        public List<Map<String, Object>> getInvoices() { return invoices; }
        public void setInvoices(List<Map<String, Object>> invoices) { this.invoices = invoices; }
        public List<Map<String, Object>> getPurchaseOrders() { return purchaseOrders; }
        public void setPurchaseOrders(List<Map<String, Object>> purchaseOrders) { this.purchaseOrders = purchaseOrders; }
        public List<Map<String, Object>> getExpenses() { return expenses; }
        public void setExpenses(List<Map<String, Object>> expenses) { this.expenses = expenses; }
    }
}
