package com.billing.simple.billsoft.forecast.model;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Simple Exponential Smoothing (SES) for smooth, steady demand patterns without strong trend/seasonality.
 * Level update: l_t = alpha * y_t + (1 - alpha) * l_{t-1}
 */
@Component
public class SimpleExponentialSmoothingModel implements TimeSeriesDemandModel {

    private static final double DEFAULT_ALPHA = 0.2;

    @Override
    public String getModelName() {
        return "SES";
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

        double alpha = DEFAULT_ALPHA;
        if (params != null && params.containsKey("alpha")) {
            try {
                alpha = ((Number) params.get("alpha")).doubleValue();
            } catch (Exception ignored) { }
        }
        alpha = Math.max(0.01, Math.min(0.99, alpha));

        double level = Math.max(0.0, historicalDailyDemand.get(0) != null ? historicalDailyDemand.get(0) : 0.0);
        for (int i = 1; i < historicalDailyDemand.size(); i++) {
            Double val = historicalDailyDemand.get(i);
            double y = val != null ? Math.max(0.0, val) : 0.0;
            level = alpha * y + (1.0 - alpha) * level;
        }

        double finalForecast = Math.max(0.0, level);
        return new ArrayList<>(Collections.nCopies(horizonDays, finalForecast));
    }
}
