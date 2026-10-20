package com.billing.simple.billsoft.forecast.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductInsightResponse {
    private Long productId;
    private String productName;
    private String sku;
    private String category;
    private String itemType;
    private String unit;

    // Current State
    private BigDecimal currentPrice;
    private BigDecimal currentStock;
    private BigDecimal incomingStock;

    // Historical Volume Analytics
    private BigDecimal grossUnitsSold;
    private BigDecimal fulfilledUnitsSold;
    private BigDecimal shortageUnits;
    private BigDecimal returnedUnits;
    private BigDecimal netUnitsSold;

    // Financial Analytics (Rupees)
    private BigDecimal grossSales;
    private BigDecimal refunds;
    private BigDecimal netSales; // Tax-inclusive

    // Realized Price Analytics
    private BigDecimal weightedAverageSellingPrice;
    private BigDecimal minRealizedPrice;
    private BigDecimal maxRealizedPrice;

    // Customer Intelligence
    private Integer uniqueCustomerCount;
    private LocalDate lastSoldDate;

    // Horizon Forecast Summaries (Derived from daily trajectory)
    private BigDecimal forecast30Days;
    private BigDecimal forecast90Days;
    private BigDecimal forecast180Days;
    private BigDecimal forecast365Days;

    // Restock & Stockout Telemetry
    private BigDecimal stockCoverageDays;
    private LocalDate expectedStockoutDate;
    private BigDecimal recommendedOrderQty;
    private String restockUrgency;
    private String forecastConfidenceLevel;
    private BigDecimal forecastConfidenceScore;
}
