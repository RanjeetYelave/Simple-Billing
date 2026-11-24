package com.billing.simple.billsoft.forecast.selection;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic demand classification based on Syntetos-Boylan Matrix:
 * ADI (Average Demand Interval) threshold: 1.32
 * CV^2 (Coefficient of Variation squared) threshold: 0.49
 *
 * Censored observation support: Stockout periods where zero demand was observed due to
 * physical inventory unavailability are excluded from total observation intervals so they
 * do not artificially inflate the ADI.
 */
@Component
public class DemandPatternClassifier {

    public static final double ADI_THRESHOLD = 1.32;
    public static final double CV2_THRESHOLD = 0.49;
    public static final int MIN_OBSERVATIONS_FOR_CLASSIFICATION = 14;
    public static final int MIN_OBSERVATIONS_FOR_HOLT_WINTERS = 60;

    public DemandClassification classify(List<Double> historicalDailyDemand) {
        return classify(historicalDailyDemand, Collections.emptyList());
    }

    public DemandClassification classify(List<Double> historicalDailyDemand, List<Boolean> stockoutFlags) {
        if (historicalDailyDemand == null || historicalDailyDemand.size() < MIN_OBSERVATIONS_FOR_CLASSIFICATION) {
            int n = historicalDailyDemand != null ? historicalDailyDemand.size() : 0;
            return DemandClassification.builder()
                    .pattern("LIMITED")
                    .adi(0.0)
                    .cv2(0.0)
                    .nonZeroCount(0)
                    .totalObservations(n)
                    .uncensoredObservations(n)
                    .stockoutDaysCount(0)
                    .recommendedModel("HISTORICAL_MEAN")
                    .build();
        }

        int totalObservations = historicalDailyDemand.size();
        List<Double> nonZeroDemands = new ArrayList<>();
        int nonZeroCount = 0;
        int stockoutDaysCount = 0;

        for (int i = 0; i < totalObservations; i++) {
            Double d = historicalDailyDemand.get(i);
            double y = d != null ? Math.max(0.0, d) : 0.0;
            boolean isStockout = (stockoutFlags != null && i < stockoutFlags.size() && Boolean.TRUE.equals(stockoutFlags.get(i)));

            if (y > 0.0) {
                nonZeroDemands.add(y);
                nonZeroCount++;
            } else if (isStockout) {
                stockoutDaysCount++;
            }
        }

        int uncensoredObservations = Math.max(nonZeroCount, totalObservations - stockoutDaysCount);

        if (nonZeroCount == 0) {
            return DemandClassification.builder()
                    .pattern("LIMITED")
                    .adi(Double.MAX_VALUE)
                    .cv2(0.0)
                    .nonZeroCount(0)
                    .totalObservations(totalObservations)
                    .uncensoredObservations(uncensoredObservations)
                    .stockoutDaysCount(stockoutDaysCount)
                    .recommendedModel("HISTORICAL_MEAN")
                    .build();
        }

        // ADI uses uncensored in-stock observation periods so stockouts do not artificially inflate demand intervals
        double adi = (double) uncensoredObservations / (double) nonZeroCount;

        // Calculate mean and variance of non-zero observations
        double sum = 0.0;
        for (Double val : nonZeroDemands) sum += val;
        double mean = sum / nonZeroCount;

        double varSum = 0.0;
        for (Double val : nonZeroDemands) {
            varSum += Math.pow(val - mean, 2);
        }
        double variance = nonZeroCount > 1 ? (varSum / (nonZeroCount - 1)) : 0.0;
        double stdDev = Math.sqrt(variance);
        double cv2 = mean > 0.0 ? Math.pow(stdDev / mean, 2) : 0.0;

        String pattern;
        String recommendedModel;

        if (adi < ADI_THRESHOLD && cv2 < CV2_THRESHOLD) {
            pattern = "SMOOTH";
            recommendedModel = totalObservations >= MIN_OBSERVATIONS_FOR_HOLT_WINTERS ? "HOLT_WINTERS" : "SES";
        } else if (adi >= ADI_THRESHOLD && cv2 < CV2_THRESHOLD) {
            pattern = "INTERMITTENT";
            recommendedModel = "CROSTON";
        } else if (adi < ADI_THRESHOLD && cv2 >= CV2_THRESHOLD) {
            pattern = "ERRATIC";
            recommendedModel = "SBA";
        } else {
            pattern = "LUMPY";
            recommendedModel = "TSB";
        }

        return DemandClassification.builder()
                .pattern(pattern)
                .adi(adi)
                .cv2(cv2)
                .nonZeroCount(nonZeroCount)
                .totalObservations(totalObservations)
                .uncensoredObservations(uncensoredObservations)
                .stockoutDaysCount(stockoutDaysCount)
                .recommendedModel(recommendedModel)
                .build();
    }
}
