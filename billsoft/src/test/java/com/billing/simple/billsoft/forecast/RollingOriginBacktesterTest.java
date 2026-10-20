package com.billing.simple.billsoft.forecast;

import com.billing.simple.billsoft.forecast.backtest.BacktestResult;
import com.billing.simple.billsoft.forecast.backtest.RollingOriginBacktester;
import com.billing.simple.billsoft.forecast.model.HistoricalMeanModel;
import com.billing.simple.billsoft.forecast.model.SimpleExponentialSmoothingModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RollingOriginBacktesterTest {

    private final RollingOriginBacktester backtester = new RollingOriginBacktester();

    @Test
    @DisplayName("Backtesting with insufficient points returns default large error metrics")
    void testInsufficientPoints() {
        List<Double> shortHistory = Arrays.asList(10.0, 20.0);
        BacktestResult result = backtester.evaluate(new HistoricalMeanModel(), shortHistory, Collections.emptyMap());

        assertNotNull(result);
        assertEquals(0.0, result.getWape());
        assertEquals(0.0, result.getRmse());
        assertEquals(0.0, result.getMae());
    }

    @Test
    @DisplayName("Backtesting produces valid WAPE, RMSE, MAE metrics on adequate series")
    void testAdequateSeriesBacktest() {
        List<Double> history = Arrays.asList(
                10.0, 12.0, 11.0, 13.0, 12.0, 14.0, 15.0, 14.0, 16.0, 15.0,
                17.0, 18.0, 17.0, 19.0, 20.0, 19.0, 21.0, 22.0, 21.0, 23.0
        );

        BacktestResult resultMean = backtester.evaluate(new HistoricalMeanModel(), history, Collections.emptyMap());
        BacktestResult resultSes = backtester.evaluate(new SimpleExponentialSmoothingModel(), history, Collections.emptyMap());

        assertNotNull(resultMean);
        assertNotNull(resultSes);

        assertTrue(resultMean.getWape() >= 0.0);
        assertTrue(resultMean.getRmse() >= 0.0);
        assertTrue(resultMean.getMae() >= 0.0);

        assertTrue(resultSes.getWape() >= 0.0);
    }
}
