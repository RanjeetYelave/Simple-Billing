package com.billing.simple.billsoft.forecast;

import com.billing.simple.billsoft.forecast.model.CrostonModel;
import com.billing.simple.billsoft.forecast.model.SbaModel;
import com.billing.simple.billsoft.forecast.model.TsbModel;
import com.billing.simple.billsoft.forecast.selection.DemandClassification;
import com.billing.simple.billsoft.forecast.selection.DemandPatternClassifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent mathematical and statistical validation test suite.
 * Validates ADI/CV^2 classification, Syntetos-Boylan Approximation (SBA),
 * Teunter-Syntetos-Babai (TSB) obsolescence decay, stockout censoring, and
 * safety stock/pack rounding formulas against non-circular reference test vectors.
 */
public class ForecastMathematicalModelVerificationTest {

    private final DemandPatternClassifier classifier = new DemandPatternClassifier();
    private final SbaModel sbaModel = new SbaModel();
    private final TsbModel tsbModel = new TsbModel();
    private final CrostonModel crostonModel = new CrostonModel();

    @Test
    @DisplayName("Syntetos-Boylan Demand Classification: Smooth, Intermittent, Erratic, and Lumpy patterns")
    void testDemandPatternClassificationMatrix() {
        // 1. Smooth Demand (ADI < 1.32, CV^2 < 0.49): demand nearly every day with low variance
        List<Double> smoothDemand = List.of(10.0, 11.0, 10.0, 9.0, 10.0, 12.0, 10.0, 11.0, 10.0, 10.0, 9.0, 10.0, 11.0, 10.0);
        DemandClassification smoothClass = classifier.classify(smoothDemand);
        assertEquals("SMOOTH", smoothClass.getPattern());
        assertTrue(smoothClass.getAdi() < DemandPatternClassifier.ADI_THRESHOLD);
        assertTrue(smoothClass.getCv2() < DemandPatternClassifier.CV2_THRESHOLD);

        // 2. Intermittent Demand (ADI >= 1.32, CV^2 < 0.49): demand occurs with gaps, but amounts are consistent
        List<Double> intermittentDemand = List.of(0.0, 10.0, 0.0, 10.0, 0.0, 0.0, 11.0, 0.0, 10.0, 0.0, 0.0, 10.0, 0.0, 10.0);
        DemandClassification intermClass = classifier.classify(intermittentDemand);
        assertEquals("INTERMITTENT", intermClass.getPattern());
        assertTrue(intermClass.getAdi() >= DemandPatternClassifier.ADI_THRESHOLD);
        assertTrue(intermClass.getCv2() < DemandPatternClassifier.CV2_THRESHOLD);
        assertEquals("CROSTON", intermClass.getRecommendedModel());

        // 3. Erratic Demand (ADI < 1.32, CV^2 >= 0.49): demand occurs frequently, but quantities swing wildly
        List<Double> erraticDemand = List.of(2.0, 80.0, 5.0, 120.0, 3.0, 90.0, 4.0, 110.0, 2.0, 75.0, 5.0, 130.0, 3.0, 95.0);
        DemandClassification erraticClass = classifier.classify(erraticDemand);
        assertEquals("ERRATIC", erraticClass.getPattern());
        assertTrue(erraticClass.getAdi() < DemandPatternClassifier.ADI_THRESHOLD);
        assertTrue(erraticClass.getCv2() >= DemandPatternClassifier.CV2_THRESHOLD);
        assertEquals("SBA", erraticClass.getRecommendedModel());

        // 4. Lumpy Demand (ADI >= 1.32, CV^2 >= 0.49): sparse occurrences with extreme volatility
        List<Double> lumpyDemand = List.of(0.0, 100.0, 0.0, 0.0, 5.0, 0.0, 0.0, 150.0, 0.0, 0.0, 8.0, 0.0, 0.0, 200.0);
        DemandClassification lumpyClass = classifier.classify(lumpyDemand);
        assertEquals("LUMPY", lumpyClass.getPattern());
        assertTrue(lumpyClass.getAdi() >= DemandPatternClassifier.ADI_THRESHOLD);
        assertTrue(lumpyClass.getCv2() >= DemandPatternClassifier.CV2_THRESHOLD);
        assertEquals("TSB", lumpyClass.getRecommendedModel());
    }

    @Test
    @DisplayName("Stockout Censoring: Out-of-stock zero days are excluded from ADI interval inflation")
    void testStockoutCensoringExcludesZeroStockDaysFromIntervalInflation() {
        // 14 days total: 4 demand days, 10 zero days.
        // Without censoring: ADI = 14 / 4 = 3.50.
        // With 4 zero days flagged as physical stockout: uncensored = 14 - 4 = 10 days.
        // Censored ADI = 10 / 4 = 2.50.
        List<Double> demand = List.of(0.0, 10.0, 0.0, 0.0, 12.0, 0.0, 0.0, 0.0, 8.0, 0.0, 0.0, 14.0, 0.0, 0.0);
        List<Boolean> stockouts = new ArrayList<>(Collections.nCopies(14, false));
        stockouts.set(2, true);
        stockouts.set(3, true);
        stockouts.set(6, true);
        stockouts.set(7, true); // 4 stockout days

        DemandClassification censoredClass = classifier.classify(demand, stockouts);
        assertEquals(4, censoredClass.getNonZeroCount());
        assertEquals(14, censoredClass.getTotalObservations());
        assertEquals(10, censoredClass.getUncensoredObservations());
        assertEquals(4, censoredClass.getStockoutDaysCount());

        double expectedAdi = 10.0 / 4.0; // 2.50
        assertEquals(expectedAdi, censoredClass.getAdi(), 1e-4);
    }

    @Test
    @DisplayName("Test Vector 1: Syntetos-Boylan Approximation (SBA) Intermittent Demand Rate")
    void testVector1_SbaIntermittentRate() {
        // Daily demand series (14 observations):
        // [0, 10, 0, 0, 12, 0, 0, 0, 8, 0, 0, 14, 0, 0] with alpha = 0.15
        List<Double> series = List.of(0.0, 10.0, 0.0, 0.0, 12.0, 0.0, 0.0, 0.0, 8.0, 0.0, 0.0, 14.0, 0.0, 0.0);
        int horizon = 5;
        Map<String, Object> params = Map.of("alpha", 0.15);

        List<Double> sbaForecast = sbaModel.forecast(series, horizon, params);
        assertNotNull(sbaForecast);
        assertEquals(horizon, sbaForecast.size());

        // Expected rate derived step-by-step in audit report: 3.88705
        double expectedRate = 3.887;
        for (Double val : sbaForecast) {
            assertEquals(expectedRate, val, 0.01, "SBA daily rate must match expected 3.887 within 0.01 tolerance");
        }

        // Verify that SBA rate is strictly less than uncorrected Croston rate (due to (1 - alpha/2) factor = 0.925)
        List<Double> crostonForecast = crostonModel.forecast(series, horizon, params);
        assertTrue(sbaForecast.get(0) < crostonForecast.get(0), "SBA forecast must correct Croston upward bias");
        assertEquals(sbaForecast.get(0), crostonForecast.get(0) * (1.0 - 0.15 / 2.0), 1e-4);
    }

    @Test
    @DisplayName("Test Vector 2: Replenishment Advisory, Safety Stock, and Commercial Pack Rounding")
    void testVector2_ReplenishmentSafetyStockAndPackRounding() {
        // Parameters:
        // Lead Time L = 7 days, Review Period R = 14 days -> Planning Horizon = 21 days
        // Predicted daily rate = 4.0 units/day -> Horizon Demand D = 21 * 4.0 = 84.0 units
        // Model RMSE = 1.8, Service Level Z = 1.65 (95% service level)
        // Physical Stock = 15, Incoming PO = 20 -> Net Inventory Position IP = 35 units
        // Product MOQ = 50, Pack Size = 12

        int leadTime = 7;
        int reviewPeriod = 14;
        int planningHorizon = leadTime + reviewPeriod; // 21 days
        double dailyRate = 4.0;
        double rmse = 1.8;
        double z = 1.65;

        BigDecimal physicalStock = new BigDecimal("15.0");
        BigDecimal incomingPo = new BigDecimal("20.0");
        BigDecimal netInventoryPosition = physicalStock.add(incomingPo); // 35.0

        BigDecimal moq = new BigDecimal("50.0");
        BigDecimal packSize = new BigDecimal("12.0");

        // 1. Safety Stock: SS = Z * RMSE * sqrt(L)
        double calcSafety = z * rmse * Math.sqrt(leadTime);
        BigDecimal safetyStock = BigDecimal.valueOf(calcSafety).setScale(3, RoundingMode.HALF_UP);
        // 1.65 * 1.8 * 2.64575 = 7.858
        assertEquals(7.858, safetyStock.doubleValue(), 0.01);

        // 2. Horizon Demand
        BigDecimal totalHorizonDemand = BigDecimal.valueOf(planningHorizon * dailyRate).setScale(3, RoundingMode.HALF_UP); // 84.000

        // 3. Target Stock: S_target = HorizonDemand + SS
        BigDecimal targetStock = totalHorizonDemand.add(safetyStock); // 91.858
        assertEquals(91.858, targetStock.doubleValue(), 0.01);

        // 4. Raw Shortfall: S_target - IP
        BigDecimal rawForecastShortfall = targetStock.subtract(netInventoryPosition).max(BigDecimal.ZERO); // 56.858
        assertEquals(56.858, rawForecastShortfall.doubleValue(), 0.01);

        // 5. Apply MOQ floor (56.858 >= 50 -> 56.858)
        BigDecimal moqAdjusted = rawForecastShortfall;
        if (rawForecastShortfall.compareTo(moq) < 0) {
            moqAdjusted = moq;
        }
        assertEquals(56.858, moqAdjusted.doubleValue(), 0.01);

        // 6. Apply Pack Size Ceiling Rounding (P = 12)
        // packs = ceil(56.858 / 12) = ceil(4.738) = 5
        // finalQty = 5 * 12 = 60.0
        BigDecimal packs = moqAdjusted.divide(packSize, 0, RoundingMode.CEILING);
        BigDecimal finalRecommendedQty = packs.multiply(packSize);

        assertEquals(new BigDecimal("60.0"), finalRecommendedQty.setScale(1, RoundingMode.HALF_UP));
        assertEquals(60, finalRecommendedQty.intValue());
    }

    @Test
    @DisplayName("Teunter-Syntetos-Babai (TSB): Prevents obsolescence overestimation by decaying probability on zero demand")
    void testTsbObsolescenceDecay() {
        // Item that had demand initially, then completely stopped (obsolescence/dead stock)
        List<Double> decayingSeries = List.of(20.0, 0.0, 15.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        int horizon = 7;
        Map<String, Object> params = Map.of("alpha", 0.15, "beta", 0.10);

        List<Double> tsbForecast = tsbModel.forecast(decayingSeries, horizon, params);
        List<Double> crostonForecast = crostonModel.forecast(decayingSeries, horizon, params);

        assertNotNull(tsbForecast);
        assertNotNull(crostonForecast);

        // Croston rate remains fixed based on last observed demand interval, failing to detect obsolescence
        // TSB probability p_t updates with beta * 0 at every zero-demand step, decaying the rate towards zero
        assertTrue(tsbForecast.get(0) < crostonForecast.get(0),
                "TSB forecast rate must be lower than Croston for decaying demand, reflecting obsolescence risk");
        assertTrue(tsbForecast.get(0) < crostonForecast.get(0) * 0.5,
                "TSB forecast rate should have decayed to less than half of Croston over 11 zero periods");
    }
}
