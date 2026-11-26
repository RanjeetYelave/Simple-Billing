package com.billing.simple.billsoft.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastAnalyticsStatementResponse {
    private Long firmId;
    private String firmName;
    private String firmAddress;
    private String firmGstin;
    private String firmPhone;
    private LocalDate asOfDate;
    private Integer horizonDays;
    private String categoryFilter;
    private String urgencyFilter;

    // Executive KPI Summaries
    private int totalProducts;
    private BigDecimal totalProjectedDemand;
    private BigDecimal totalShortfallUnits;
    private BigDecimal estimatedReplenishmentCost;
    private int outOfStockCount;
    private int criticalCount;
    private int reorderNeededCount;
    private int healthyCount;

    // Categorical Summary Matrix
    private List<CategorySummary> categoryBreakdown;

    // Product Detail Ledger
    private List<ProductLedgerItem> productLedger;

    private String methodologyNote;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategorySummary {
        private String category;
        private int productCount;
        private BigDecimal projectedDemand;
        private BigDecimal shortfallUnits;
        private BigDecimal estimatedCapital;
        private int outOfStockCount;
        private int criticalCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductLedgerItem {
        private Long productId;
        private String productName;
        private String sku;
        private String barcode;
        private String hsnCode;
        private String category;
        private String unit;
        private BigDecimal currentStock;
        private BigDecimal incomingStock;
        private BigDecimal sales30d;
        private BigDecimal dailyVelocity;
        private Integer daysOfCoverage;
        private BigDecimal projectedHorizonDemand;
        private BigDecimal recommendedOrderQty;
        private BigDecimal costPrice;
        private BigDecimal estimatedReplenishmentCost;
        private String urgencyStatus;
        private String selectedModel;
        private BigDecimal confidenceScore;
        private String primaryDriver;
    }
}
