package com.billing.simple.billsoft.forecast.confidence;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Deterministic forecast reliability scoring engine.
 * Computes an explainable 0 - 100 confidence score and assigns qualitative bands (HIGH, MEDIUM, LOW, PROVISIONAL).
 *
 * Requirements:
 * 1. Insufficient history (< 14 days) explicitly assigned "PROVISIONAL" status; zero error
 *    is never assumed when data is inadequate to backtest.
 * 2. Transparent multi-factor weighting: WAPE penalty (up to 50 pts), Zero-demand rate penalty (up to 20 pts),
 *    Demand variability CV^2 penalty (up to 15 pts), and history coverage penalty.
 */
@Component
public class ForecastConfidenceCalculator {

    public static class ConfidenceResult {
        private final BigDecimal score;
        private final String level; // "HIGH", "MEDIUM", "LOW", "PROVISIONAL"
        private final boolean provisional;

        public ConfidenceResult(BigDecimal score, String level) {
            this(score, level, "PROVISIONAL".equalsIgnoreCase(level));
        }

        public ConfidenceResult(BigDecimal score, String level, boolean provisional) {
            this.score = score;
            this.level = level;
            this.provisional = provisional;
        }

        public BigDecimal getScore() { return score; }
        public String getLevel() { return level; }
        public boolean isProvisional() { return provisional; }
    }

    public ConfidenceResult calculate(double wape, double zeroDemandRate, double cv2, int historyDays) {
        return calculate(wape, zeroDemandRate, cv2, historyDays, historyDays >= 14);
    }

    public ConfidenceResult calculate(double wape, double zeroDemandRate, double cv2, int historyDays, boolean hasSufficientHistory) {
        if (historyDays < 14 || !hasSufficientHistory) {
            // Insufficient history for statistical validation -> Explicit PROVISIONAL classification
            double provisionalScore = Math.max(10.0, Math.min(35.0, 10.0 + (historyDays * 1.75)));
            BigDecimal score = BigDecimal.valueOf(provisionalScore).setScale(2, RoundingMode.HALF_UP);
            return new ConfidenceResult(score, "PROVISIONAL", true);
        }

        int historyPenalty;
        if (historyDays < 30) {
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

        return new ConfidenceResult(score, level, false);
    }
}
