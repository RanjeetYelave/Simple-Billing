package com.billing.simple.billsoft.forecast.confidence;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Deterministic forecast reliability scoring engine.
 * Computes an explainable 0 - 100 confidence score and assigns qualitative bands (HIGH, MEDIUM, LOW).
 */
@Component
public class ForecastConfidenceCalculator {

    public static class ConfidenceResult {
        private final BigDecimal score;
        private final String level; // "HIGH", "MEDIUM", "LOW"

        public ConfidenceResult(BigDecimal score, String level) {
            this.score = score;
            this.level = level;
        }

        public BigDecimal getScore() { return score; }
        public String getLevel() { return level; }
    }

    public ConfidenceResult calculate(double wape, double zeroDemandRate, double cv2, int historyDays) {
        int historyPenalty;
        if (historyDays < 14) {
            historyPenalty = 35;
        } else if (historyDays < 30) {
            historyPenalty = 20;
        } else if (historyDays < 90) {
            historyPenalty = 10;
        } else {
            historyPenalty = 0;
        }

        double penaltyWape = 50.0 * Math.min(1.0, Math.max(0.0, wape));
        double penaltyZeroDemand = 20.0 * Math.min(1.0, Math.max(0.0, zeroDemandRate));
        double penaltyCv2 = 15.0 * Math.min(1.0, Math.max(0.0, cv2));

        double totalPenalty = penaltyWape + penaltyZeroDemand + penaltyCv2 + historyPenalty;
        double rawScore = 100.0 - totalPenalty;
        double clampedScore = Math.max(0.0, Math.min(100.0, rawScore));

        BigDecimal score = BigDecimal.valueOf(clampedScore).setScale(2, RoundingMode.HALF_UP);

        String level;
        if (clampedScore >= 75.0) {
            level = "HIGH";
        } else if (clampedScore >= 50.0) {
            level = "MEDIUM";
        } else {
            level = "LOW";
        }

        return new ConfidenceResult(score, level);
    }
}
