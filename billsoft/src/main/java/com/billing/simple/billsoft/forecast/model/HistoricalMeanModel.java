package com.billing.simple.billsoft.forecast.model;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Baseline fallback model for products with limited sales history (<14 observations).
 * Produces a constant daily projection equal to the historical sample mean.
 */
@Component
public class HistoricalMeanModel implements TimeSeriesDemandModel {

    @Override
    public String getModelName() {
        return "HISTORICAL_MEAN";
    }

    @Override
    public String getModelVersion() {
        return "1.0.0";
    }

    @Override
    public List<Double> forecast(List<Double> historicalDailyDemand, int horizonDays, Map<String, Object> params) {
        if (horizonDays <= 0) return Collections.emptyList();
        if (historicalDailyDemand == null || historicalDailyDemand.isEmpty()) {
            return new ArrayList<>(Collections.nCopies(horizonDays, 0.0));
        }

        double sum = 0.0;
        int count = 0;
        for (Double d : historicalDailyDemand) {
            if (d != null) {
                sum += Math.max(0.0, d);
                count++;
            }
        }

        double mean = count > 0 ? (sum / count) : 0.0;
        return new ArrayList<>(Collections.nCopies(horizonDays, Math.max(0.0, mean)));
    }
}
