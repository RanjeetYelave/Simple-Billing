package com.billing.simple.billsoft.dtos;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FirmAnalyticsResponse {

    // High-level amounts
    private Double totalBusiness;      // all invoices
    private Double totalPaid;          // paid invoices
    private Double totalPending;       // unpaid amount

    // Period stats
    private Double businessToday;
    private Double businessThisWeek;
    private Double businessThisMonth;
    private Double businessThisYear;

    // Top entities
    private List<TopCustomer> topCustomers;
    private List<TopProduct> topProducts;

    @Getter
    @Setter
    public static class TopCustomer {
        private Long customerId;
        private String customerName;
        private Double totalAmount;
        private Double pendingAmount;
        private Long invoiceCount;
    }

    @Getter
    @Setter
    public static class TopProduct {
        private Long productId;
        private String productName;
        private Long totalQty;
        private Double totalAmount;
    }
}
