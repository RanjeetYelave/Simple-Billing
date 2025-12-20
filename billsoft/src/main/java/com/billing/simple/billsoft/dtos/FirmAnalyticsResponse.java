package com.billing.simple.billsoft.dtos;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Analytics for the entire firm (dashboard).
 */
@Getter
@Setter
public class FirmAnalyticsResponse {

    // High-level totals
    private Double totalBusiness;
    private Double totalPaid;
    private Double totalPending;

    // Period data
    private Double businessToday;
    private Double businessThisWeek;
    private Double businessThisMonth;
    private Double businessThisYear;

    // Rankings
    private List<TopCustomer> topCustomers;
    private List<TopProduct> topProducts;

    @Getter @Setter @NoArgsConstructor
    public static class TopCustomer {
        private Long customerId;
        private String customerName;
        private Double totalAmount;
        private Double pendingAmount;
        private Long invoiceCount;
    }

    @Getter @Setter @NoArgsConstructor
    public static class TopProduct {
        private Long productId;
        private String productName;
        private Long totalQty;
        private Double totalAmount;
    }
}
