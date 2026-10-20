package com.billing.simple.billsoft.forecast;

import com.billing.simple.billsoft.forecast.selection.DemandClassification;
import com.billing.simple.billsoft.forecast.selection.DemandPatternClassifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DemandPatternClassifierTest {

    private final DemandPatternClassifier classifier = new DemandPatternClassifier();

    @Test
    @DisplayName("Short history (<14 observations) classified as LIMITED")
    void testShortHistory() {
        DemandClassification res = classifier.classify(Arrays.asList(10.0, 12.0, 15.0));
        assertEquals("LIMITED", res.getPattern());
        assertEquals("HISTORICAL_MEAN", res.getRecommendedModel());
    }

    @Test
    @DisplayName("Smooth demand: constant/low variance frequent demand (ADI < 1.32, CV2 < 0.49)")
    void testSmoothDemand() {
        // 20 observations, regular demand every day with low variance
        List<Double> demand = Arrays.asList(
                10.0, 11.0, 10.5, 9.8, 10.2, 11.1, 10.0, 10.8,
                10.2, 10.0, 9.9, 10.4, 11.0, 10.3, 10.1, 10.5,
                9.7, 10.6, 10.2, 10.0
        );
        DemandClassification res = classifier.classify(demand);

        assertEquals("SMOOTH", res.getPattern());
        assertTrue(res.getAdi() < 1.32, "ADI should be < 1.32 but was " + res.getAdi());
        assertTrue(res.getCv2() < 0.49, "CV2 should be < 0.49 but was " + res.getCv2());
    }

    @Test
    @DisplayName("Intermittent demand: low frequency but steady size (ADI >= 1.32, CV2 < 0.49)")
    void testIntermittentDemand() {
        // Demand every 3rd period with equal quantity across 21 periods
        List<Double> demand = Arrays.asList(
                10.0, 0.0, 0.0, 10.0, 0.0, 0.0, 10.0, 0.0, 0.0,
                10.0, 0.0, 0.0, 10.0, 0.0, 0.0, 10.0, 0.0, 0.0,
                10.0, 0.0, 0.0
        );
        DemandClassification res = classifier.classify(demand);

        assertEquals("INTERMITTENT", res.getPattern());
        assertTrue(res.getAdi() >= 1.32, "ADI should be >= 1.32 but was " + res.getAdi());
        assertTrue(res.getCv2() < 0.49, "CV2 should be < 0.49 but was " + res.getCv2());
    }

    @Test
    @DisplayName("Erratic demand: high frequency but volatile size (ADI < 1.32, CV2 >= 0.49)")
    void testErraticDemand() {
        // Demand every period but wildly varying quantities across 20 periods
        List<Double> demand = Arrays.asList(
                1.0, 100.0, 2.0, 80.0, 3.0, 95.0, 1.0, 110.0,
                2.0, 90.0, 1.0, 120.0, 3.0, 85.0, 2.0, 105.0,
                1.0, 95.0, 2.0, 100.0
        );
        DemandClassification res = classifier.classify(demand);

        assertEquals("ERRATIC", res.getPattern());
        assertTrue(res.getAdi() < 1.32, "ADI should be < 1.32 but was " + res.getAdi());
        assertTrue(res.getCv2() >= 0.49, "CV2 should be >= 0.49 but was " + res.getCv2());
    }

    @Test
    @DisplayName("Lumpy demand: infrequent and highly volatile size (ADI >= 1.32, CV2 >= 0.49)")
    void testLumpyDemand() {
        // Sporadic periods with high variance across 20 periods
        List<Double> demand = Arrays.asList(
                0.0, 0.0, 100.0, 0.0, 0.0, 0.0, 5.0, 0.0, 0.0, 80.0,
                0.0, 0.0, 120.0, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 90.0
        );
        DemandClassification res = classifier.classify(demand);

        assertEquals("LUMPY", res.getPattern());
        assertTrue(res.getAdi() >= 1.32, "ADI should be >= 1.32 but was " + res.getAdi());
        assertTrue(res.getCv2() >= 0.49, "CV2 should be >= 0.49 but was " + res.getCv2());
    }
}
