package com.billing.simple.billsoft.forecast.backtest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BacktestResult {
    private String modelName;
    private double wape;
    private double rmse;
    private double mae;
    private int foldsEvaluated;
    private int validationHorizonDays;
}
