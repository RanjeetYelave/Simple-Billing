package com.billing.simple.billsoft.forecast.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryIntelligenceSummaryDto {
    private int totalProducts;
    private int outOfStockCount;
    private int criticalCount;
    private int reorderNeededCount;
    private int healthyCount;
    private BigDecimal totalReplenishmentUnits;
    private Map<Long, ItemSummary> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemSummary {
        private Long productId;
        private String productName;
        private BigDecimal currentStock;
        private BigDecimal minStockLevel;
        private BigDecimal sales30d;
        private BigDecimal dailyVelocity;
        private Integer daysOfCoverage;
        private String urgencyStatus;
        private BigDecimal recommendedOrderQty;
        private BigDecimal incomingStock;
        private LocalDate expectedStockoutDate;
        private String confidenceLevel;
    }
}
