package com.billing.simple.billsoft.forecast.model;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Teunter-Syntetos-Babai (TSB) model for lumpy/decaying intermittent demand.
 * Updates demand probability p_t at every time step (even zero demand days), preventing Croston over-estimation:
 * p_t = beta * (y_t > 0 ? 1 : 0) + (1 - beta) * p_{t-1}
 * If y_t > 0: z_t = alpha * y_t + (1 - alpha) * z_{t-1}
 * Forecast rate = p_t * z_t
 */
@Component
public class TsbModel implements TimeSeriesDemandModel {

    private static final double DEFAULT_ALPHA = 0.15;
    private static final double DEFAULT_BETA = 0.10;

    @Override
    public String getModelName() {
        return "TSB";
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
        double beta = DEFAULT_BETA;
        if (params != null) {
            if (params.containsKey("alpha")) alpha = ((Number) params.get("alpha")).doubleValue();
            if (params.containsKey("beta")) beta = ((Number) params.get("beta")).doubleValue();
        }
        alpha = Math.max(0.01, Math.min(0.99, alpha));
        beta = Math.max(0.01, Math.min(0.99, beta));

        int firstNonZeroIdx = -1;
        for (int i = 0; i < historicalDailyDemand.size(); i++) {
            Double val = historicalDailyDemand.get(i);
            if (val != null && val > 0.0) {
                firstNonZeroIdx = i;
                break;
            }
        }

        if (firstNonZeroIdx == -1) {
            return new ArrayList<>(Collections.nCopies(horizonDays, 0.0));
        }

        double z = historicalDailyDemand.get(firstNonZeroIdx);
        double p = 1.0 / Math.max(1.0, firstNonZeroIdx + 1);

        for (int i = firstNonZeroIdx + 1; i < historicalDailyDemand.size(); i++) {
            Double val = historicalDailyDemand.get(i);
            double y = val != null ? Math.max(0.0, val) : 0.0;
            double k = y > 0.0 ? 1.0 : 0.0;

            p = beta * k + (1.0 - beta) * p;
            if (y > 0.0) {
                z = alpha * y + (1.0 - alpha) * z;
            }
        }

        double rate = p * z;
        return new ArrayList<>(Collections.nCopies(horizonDays, Math.max(0.0, rate)));
    }
}
