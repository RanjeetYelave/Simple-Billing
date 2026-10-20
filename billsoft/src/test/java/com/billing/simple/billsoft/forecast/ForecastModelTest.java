package com.billing.simple.billsoft.forecast;

import com.billing.simple.billsoft.forecast.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ForecastModelTest {

    @Test
    @DisplayName("HistoricalMeanModel returns mean of history for all forecast horizons")
    void testHistoricalMeanModel() {
        HistoricalMeanModel model = new HistoricalMeanModel();
        List<Double> history = Arrays.asList(10.0, 20.0, 30.0, 40.0);
        List<Double> forecast = model.forecast(history, 4);

        assertEquals(4, forecast.size());
        for (Double val : forecast) {
            assertEquals(25.0, val, 1e-6);
        }
    }

    @Test
    @DisplayName("HistoricalMeanModel handles empty/null history gracefully")
    void testHistoricalMeanModelEmpty() {
        HistoricalMeanModel model = new HistoricalMeanModel();
        List<Double> forecast = model.forecast(Collections.emptyList(), 3);
        assertEquals(3, forecast.size());
        for (Double val : forecast) {
            assertEquals(0.0, val, 1e-6);
        }
    }

    @Test
    @DisplayName("SimpleExponentialSmoothingModel produces valid smoothed projections")
    void testSimpleExponentialSmoothingModel() {
        SimpleExponentialSmoothingModel model = new SimpleExponentialSmoothingModel();
        List<Double> history = Arrays.asList(10.0, 12.0, 14.0, 16.0, 18.0, 20.0);
        List<Double> forecast = model.forecast(history, 5);

        assertEquals(5, forecast.size());
        assertTrue(forecast.get(0) > 10.0 && forecast.get(0) <= 20.0);
        // SES constant forecast for flat horizon
        assertEquals(forecast.get(0), forecast.get(1), 1e-6);
    }

    @Test
    @DisplayName("HoltWintersModel captures trend without negative forecasts")
    void testHoltWintersModel() {
        HoltWintersModel model = new HoltWintersModel();
        List<Double> history = Arrays.asList(
                10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0,
                24.0, 26.0, 28.0, 30.0, 32.0, 34.0, 36.0,
                38.0, 40.0, 42.0, 44.0, 46.0, 48.0, 50.0
        );
        List<Double> forecast = model.forecast(history, 3);

        assertEquals(3, forecast.size());
        assertTrue(forecast.get(0) > 30.0);
        for (Double val : forecast) {
            assertTrue(val >= 0.0);
        }
    }

    @Test
    @DisplayName("CrostonModel produces non-negative non-zero forecast for intermittent demand")
    void testCrostonModel() {
        CrostonModel model = new CrostonModel();
        List<Double> history = Arrays.asList(0.0, 5.0, 0.0, 0.0, 10.0, 0.0, 8.0, 0.0);
        List<Double> forecast = model.forecast(history, 4);

        assertEquals(4, forecast.size());
        assertTrue(forecast.get(0) > 0);
        for (Double val : forecast) {
            assertTrue(val >= 0.0);
        }
    }

    @Test
    @DisplayName("SbaModel applies Syntetos-Boylan bias correction (1 - alpha/2)")
    void testSbaModel() {
        double alpha = 0.2;
        Map<String, Object> params = new HashMap<>();
        params.put("alpha", alpha);

        CrostonModel croston = new CrostonModel();
        SbaModel sba = new SbaModel();

        List<Double> history = Arrays.asList(0.0, 10.0, 0.0, 0.0, 10.0, 0.0, 0.0, 10.0);
        List<Double> crostonFc = croston.forecast(history, 1, params);
        List<Double> sbaFc = sba.forecast(history, 1, params);

        assertEquals(1, crostonFc.size());
        assertEquals(1, sbaFc.size());
        // SBA = (1 - alpha/2) * Croston = 0.9 * Croston
        assertEquals(crostonFc.get(0) * (1.0 - alpha / 2.0), sbaFc.get(0), 1e-4);
    }

    @Test
    @DisplayName("TsbModel updates probability at each period")
    void testTsbModel() {
        TsbModel model = new TsbModel();
        List<Double> history = Arrays.asList(0.0, 0.0, 0.0, 5.0, 0.0, 0.0, 10.0, 0.0, 0.0);
        List<Double> forecast = model.forecast(history, 3);

        assertEquals(3, forecast.size());
        assertTrue(forecast.get(0) >= 0.0);
    }
}
