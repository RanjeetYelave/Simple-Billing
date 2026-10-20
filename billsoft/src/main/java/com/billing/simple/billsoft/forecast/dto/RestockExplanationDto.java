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
public class RestockExplanationDto {
    private Long productId;
    private String productName;
    private String sku;

    // Configured Parameters
    private Integer leadTimeDays;
    private Integer reviewPeriodDays;
    private Integer planningHorizonDays;
    private BigDecimal minimumOrderQty;
    private BigDecimal packSize;

    // Current Inventory Position
    private BigDecimal currentStock;
    private BigDecimal incomingIssuedPoStock;
    private BigDecimal netInventoryPosition;

    // Demand Forecast Over Planning Horizon
    private BigDecimal leadTimeDemand;
    private BigDecimal reviewPeriodDemand;
    private BigDecimal totalPlanningHorizonDemand;

    // Safety Stock & Target
    private BigDecimal safetyStock;
    private boolean safetyStockOverridden;
    private BigDecimal targetStockLevel;

    // Mathematical Recommendation Breakdown
    private BigDecimal rawShortfall;
    private BigDecimal moqAdjusted;
    private BigDecimal finalRecommendedOrderQty;

    // Stockout & Reliability
    private LocalDate expectedStockoutDate;
    private String urgencyStatus;
    private String confidenceLevel;
    private BigDecimal confidenceScore;
    private String selectedModel;
    private String explanationText;
    private LocalDateTime generatedAt;
}
