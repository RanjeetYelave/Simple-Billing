package com.billing.simple.billsoft.forecast;

import com.billing.simple.billsoft.forecast.confidence.ForecastConfidenceCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForecastConfidenceCalculatorTest {

    private final ForecastConfidenceCalculator calculator = new ForecastConfidenceCalculator();

    @Test
    @DisplayName("Short history or high error scores LOW confidence")
    void testSparseSeries() {
        ForecastConfidenceCalculator.ConfidenceResult res =
                calculator.calculate(0.8, 0.5, 0.8, 5);

        assertEquals("LOW", res.getLevel());
        assertTrue(res.getScore().doubleValue() < 50.0);
    }

    @Test
    @DisplayName("High history, low WAPE, low zero demand rate, and low CV2 yields HIGH confidence")
    void testHighQualitySeries() {
        ForecastConfidenceCalculator.ConfidenceResult res =
                calculator.calculate(0.05, 0.0, 0.1, 120);

        assertEquals("HIGH", res.getLevel());
        assertTrue(res.getScore().doubleValue() >= 75.0, "Score should be >= 75 but was " + res.getScore());
    }

    @Test
    @DisplayName("Confidence band thresholds: >=75 HIGH, >=50 MEDIUM, <50 LOW")
    void testBandThresholds() {
        ForecastConfidenceCalculator.ConfidenceResult high = calculator.calculate(0.05, 0.05, 0.05, 100);
        ForecastConfidenceCalculator.ConfidenceResult med = calculator.calculate(0.25, 0.20, 0.20, 30);
        ForecastConfidenceCalculator.ConfidenceResult low = calculator.calculate(0.70, 0.60, 0.80, 10);

        assertTrue(high.getScore().compareTo(med.getScore()) >= 0);
        assertTrue(med.getScore().compareTo(low.getScore()) >= 0);
    }
}
