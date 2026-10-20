package com.billing.simple.billsoft.forecast.dto;

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
public class HistoricalTrendsResponse {
    private Long productId;
    private String range; // "30d", "90d", "180d", "365d", "all"
    private List<TrendPoint> points;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrendPoint {
        private LocalDate date;
        private BigDecimal grossDemand;
        private BigDecimal fulfilledDemand;
        private BigDecimal returnedDemand;
        private BigDecimal netDemand;
        private BigDecimal grossSales;
        private BigDecimal netSales;
        private BigDecimal realizedUnitPrice;
    }
}
