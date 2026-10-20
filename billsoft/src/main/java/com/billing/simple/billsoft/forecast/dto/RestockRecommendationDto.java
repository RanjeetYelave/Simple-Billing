package com.billing.simple.billsoft.forecast.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestockRecommendationDto {
    private Long id;
    private Long productId;
    private String productName;
    private String sku;
    private String category;
    private String unit;
    private BigDecimal currentStock;
    private BigDecimal incomingStock;
    private BigDecimal minStockLevel;
    private BigDecimal thresholdRequirement;
    private BigDecimal forecastRecommendedQty;
    private String recommendationReason; // "FORECAST", "MINIMUM_STOCK", "BOTH", "HEALTHY"
    private BigDecimal forecast30d;
    private BigDecimal recommendedOrderQty;
    private LocalDate expectedStockoutDate;
    private String urgencyStatus; // "OUT_OF_STOCK", "CRITICAL", "REORDER_NEEDED", "HEALTHY"
    private String confidenceLevel;
    private BigDecimal confidenceScore;
    private BigDecimal unitCost;
    private BigDecimal estimatedTotalCost;
    private String explanationText;
    private LocalDateTime generatedAt;
}
