package com.billing.simple.billsoft.dtos;

import java.util.List;

/**
 * Analytics for the entire firm (dashboard).
 */
public class FirmAnalyticsResponse {

    // High-level totals
    private Double totalBusiness;
    private Double totalPaid;
    private Double totalPending;

    // Counts & Pipelines
    private Long unpaidInvoiceCount;
    private Long overdueInvoiceCount;
    private Double quotePipelineValue;
    private Long quoteCount;

    // Period data
    private Double businessToday;
    private Double businessThisWeek;
    private Double businessThisMonth;
    private Double businessThisYear;

    // Rankings
    private List<TopCustomer> topCustomers;
    private List<TopProduct> topProducts;

    public Double getTotalBusiness() { return totalBusiness; }
    public void setTotalBusiness(Double totalBusiness) { this.totalBusiness = totalBusiness; }

    public Double getTotalPaid() { return totalPaid; }
    public void setTotalPaid(Double totalPaid) { this.totalPaid = totalPaid; }

    public Double getTotalPending() { return totalPending; }
    public void setTotalPending(Double totalPending) { this.totalPending = totalPending; }

    public Long getUnpaidInvoiceCount() { return unpaidInvoiceCount; }
    public void setUnpaidInvoiceCount(Long unpaidInvoiceCount) { this.unpaidInvoiceCount = unpaidInvoiceCount; }

    public Long getOverdueInvoiceCount() { return overdueInvoiceCount; }
    public void setOverdueInvoiceCount(Long overdueInvoiceCount) { this.overdueInvoiceCount = overdueInvoiceCount; }

    public Double getQuotePipelineValue() { return quotePipelineValue; }
    public void setQuotePipelineValue(Double quotePipelineValue) { this.quotePipelineValue = quotePipelineValue; }

    public Long getQuoteCount() { return quoteCount; }
    public void setQuoteCount(Long quoteCount) { this.quoteCount = quoteCount; }

    public Double getBusinessToday() { return businessToday; }
    public void setBusinessToday(Double businessToday) { this.businessToday = businessToday; }

    public Double getBusinessThisWeek() { return businessThisWeek; }
    public void setBusinessThisWeek(Double businessThisWeek) { this.businessThisWeek = businessThisWeek; }

    public Double getBusinessThisMonth() { return businessThisMonth; }
    public void setBusinessThisMonth(Double businessThisMonth) { this.businessThisMonth = businessThisMonth; }

    public Double getBusinessThisYear() { return businessThisYear; }
    public void setBusinessThisYear(Double businessThisYear) { this.businessThisYear = businessThisYear; }

    public List<TopCustomer> getTopCustomers() { return topCustomers; }
    public void setTopCustomers(List<TopCustomer> topCustomers) { this.topCustomers = topCustomers; }

    public List<TopProduct> getTopProducts() { return topProducts; }
    public void setTopProducts(List<TopProduct> topProducts) { this.topProducts = topProducts; }

    public static class TopCustomer {
        private Long customerId;
        private String customerName;
        private Double totalAmount;
        private Double pendingAmount;
        private Long invoiceCount;

        public TopCustomer() {}

        public Long getCustomerId() { return customerId; }
        public void setCustomerId(Long customerId) { this.customerId = customerId; }

        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }

        public Double getTotalAmount() { return totalAmount; }
        public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

        public Double getPendingAmount() { return pendingAmount; }
        public void setPendingAmount(Double pendingAmount) { this.pendingAmount = pendingAmount; }

        public Long getInvoiceCount() { return invoiceCount; }
        public void setInvoiceCount(Long invoiceCount) { this.invoiceCount = invoiceCount; }
    }

    public static class TopProduct {
        private Long productId;
        private String productName;
        private Long totalQty;
        private Double totalAmount;

        public TopProduct() {}

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public Long getTotalQty() { return totalQty; }
        public void setTotalQty(Long totalQty) { this.totalQty = totalQty; }

        public Double getTotalAmount() { return totalAmount; }
        public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    }
}
