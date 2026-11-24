package com.billing.simple.billsoft.forecast.model;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holt-Winters Additive model for continuous demand with damped trend and 7-day weekly seasonality.
 * Level: l_t = alpha * (y_t - s_{t-m}) + (1 - alpha) * (l_{t-1} + phi * b_{t-1})
 * Trend: b_t = beta * (l_t - l_{t-1}) + (1 - beta) * (phi * b_{t-1})
 * Seasonal: s_t = gamma * (y_t - l_t) + (1 - gamma) * s_{t-m}
 * Forecast: y_{t+h} = l_t + (sum_{i=1}^h phi^i) * b_t + s_{t+h-m}
 *
 * Damped trend parameter phi prevents explosive unrealistic divergence over 30d to 365d horizons.
 */
@Component
public class HoltWintersModel implements TimeSeriesDemandModel {

    private static final int SEASON_LENGTH = 7; // Weekly cycle
    private static final double DEFAULT_ALPHA = 0.2;
    private static final double DEFAULT_BETA = 0.1;
    private static final double DEFAULT_GAMMA = 0.2;
    private static final double DEFAULT_PHI = 0.90; // Standard empirical damping parameter

    @Override
    public String getModelName() {
        return "HOLT_WINTERS";
    }

    @Override
    public String getModelVersion() {
        return "1.1.0";
    }

    @Override
    public List<Double> forecast(List<Double> historicalDailyDemand, int horizonDays, Map<String, Object> params) {
        if (horizonDays <= 0) return Collections.emptyList();
        int n = historicalDailyDemand != null ? historicalDailyDemand.size() : 0;
        if (n < 2 * SEASON_LENGTH) {
            // Insufficient history for full seasonal cycles -> fallback to sample mean
            double sum = 0.0;
            int count = 0;
            if (historicalDailyDemand != null) {
                for (Double d : historicalDailyDemand) {
                    if (d != null) { sum += Math.max(0.0, d); count++; }
                }
            }
            double mean = count > 0 ? (sum / count) : 0.0;
            return new ArrayList<>(Collections.nCopies(horizonDays, Math.max(0.0, mean)));
        }

        double alpha = DEFAULT_ALPHA;
        double beta = DEFAULT_BETA;
        double gamma = DEFAULT_GAMMA;
        double phi = DEFAULT_PHI;
        if (params != null) {
            if (params.containsKey("alpha")) alpha = ((Number) params.get("alpha")).doubleValue();
            if (params.containsKey("beta")) beta = ((Number) params.get("beta")).doubleValue();
            if (params.containsKey("gamma")) gamma = ((Number) params.get("gamma")).doubleValue();
            if (params.containsKey("phi")) phi = ((Number) params.get("phi")).doubleValue();
        }

        double[] s = new double[SEASON_LENGTH];
        double firstSeasonAvg = 0.0;
        for (int i = 0; i < SEASON_LENGTH; i++) {
            Double val = historicalDailyDemand.get(i);
            firstSeasonAvg += val != null ? Math.max(0.0, val) : 0.0;
        }
        firstSeasonAvg /= SEASON_LENGTH;

        for (int i = 0; i < SEASON_LENGTH; i++) {
            Double val = historicalDailyDemand.get(i);
            s[i] = (val != null ? Math.max(0.0, val) : 0.0) - firstSeasonAvg;
        }

        double level = firstSeasonAvg;
        double trend = 0.0;

        for (int t = SEASON_LENGTH; t < n; t++) {
            Double val = historicalDailyDemand.get(t);
            double y = val != null ? Math.max(0.0, val) : 0.0;
            int sIdx = t % SEASON_LENGTH;

            double prevLevel = level;
            double prevTrend = trend;
            double prevSeason = s[sIdx];

            level = alpha * (y - prevSeason) + (1.0 - alpha) * (prevLevel + phi * prevTrend);
            trend = beta * (level - prevLevel) + (1.0 - beta) * (phi * prevTrend);
            s[sIdx] = gamma * (y - level) + (1.0 - gamma) * prevSeason;
        }

        List<Double> predictions = new ArrayList<>(horizonDays);
        for (int h = 1; h <= horizonDays; h++) {
            int sIdx = (n + h - 1) % SEASON_LENGTH;
            // Cumulative damping factor sum_{i=1}^h phi^i
            double dampSum = Math.abs(phi - 1.0) < 1e-6
                    ? (double) h
                    : phi * (1.0 - Math.pow(phi, h)) / (1.0 - phi);
            double pred = level + (dampSum * trend) + s[sIdx];
            predictions.add(Math.max(0.0, pred));
        }

        return predictions;
    }
}
