package com.billing.simple.billsoft.forecast.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastAdminSyncResponse {
    private boolean success;
    private Long firmId;
    private int invoicesProcessed;
    private int returnsProcessed;
    private int productsAggregated;
    private int forecastsGenerated;
    private int recommendationsGenerated;
    private long executionDurationMs;
    private String message;
}
