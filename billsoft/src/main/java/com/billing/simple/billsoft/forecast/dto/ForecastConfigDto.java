package com.billing.simple.billsoft.forecast.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastConfigDto {
    private Long productId;
    private Boolean forecastEnabled;
    private Integer leadTimeDays;
    private Integer reviewPeriodDays;
    private BigDecimal minimumOrderQty;
    private BigDecimal packSize;
    private BigDecimal safetyStockOverride;
    private String modelOverride;
}
