package com.billing.simple.billsoft.forecast.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastTrajectoryResponse {
    private Long productId;
    private Long modelRunId;
    private String selectedModel;
    private String demandPattern;
    private BigDecimal confidenceScore;
    private String confidenceLevel;
    private LocalDateTime generatedAt;

    // Derived cumulative horizon quantities
    private BigDecimal horizon30dDemand;
    private BigDecimal horizon90dDemand;
    private BigDecimal horizon180dDemand;
    private BigDecimal horizon365dDemand;

    // Daily predicted points (T+1 ... T+365)
    private List<DailyForecastPoint> trajectory;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyForecastPoint {
        private LocalDate date;
        private BigDecimal predictedDemand;
    }
}
