package com.billing.simple.billsoft.forecast.model;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Croston's method for intermittent demand.
 * Separately updates demand size (z) and inter-arrival interval (p) upon non-zero observations:
 * z_t = alpha * y_t + (1 - alpha) * z_{t-1}
 * p_t = alpha * q_t + (1 - alpha) * p_{t-1}
 * Forecast rate = z / p
 */
@Component
public class CrostonModel implements TimeSeriesDemandModel {

    private static final double DEFAULT_ALPHA = 0.15;

    @Override
    public String getModelName() {
        return "CROSTON";
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

        // Find first non-zero demand index for initialization
        int firstNonZeroIdx = -1;
        for (int i = 0; i < historicalDailyDemand.size(); i++) {
            Double val = historicalDailyDemand.get(i);
            if (val != null && val > 0.0) {
                firstNonZeroIdx = i;
                break;
            }
        }

        if (firstNonZeroIdx == -1) {
            // All historical observations were zero
            return new ArrayList<>(Collections.nCopies(horizonDays, 0.0));
        }

        double z = historicalDailyDemand.get(firstNonZeroIdx);
        double p = Math.max(1.0, firstNonZeroIdx + 1);
        int q = 1;

        for (int i = firstNonZeroIdx + 1; i < historicalDailyDemand.size(); i++) {
            Double val = historicalDailyDemand.get(i);
            double y = val != null ? Math.max(0.0, val) : 0.0;
            if (y > 0.0) {
                z = alpha * y + (1.0 - alpha) * z;
                p = alpha * q + (1.0 - alpha) * p;
                q = 1;
            } else {
                q++;
            }
        }

        double rate = p > 0 ? (z / p) : 0.0;
        return new ArrayList<>(Collections.nCopies(horizonDays, Math.max(0.0, rate)));
    }
}
