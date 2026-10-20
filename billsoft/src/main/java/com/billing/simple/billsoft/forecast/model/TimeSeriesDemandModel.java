package com.billing.simple.billsoft.forecast.model;

import java.util.List;
import java.util.Map;

/**
 * Interface for pure-Java time-series demand forecasting algorithms.
 */
public interface TimeSeriesDemandModel {

    String getModelName();

    String getModelVersion();

    /**
     * Forecasts future daily customer demand trajectory.
     *
     * @param historicalDailyDemand List of historical daily demand observations in chronological order
     * @param horizonDays           Number of future days to forecast (e.g. 365)
     * @param params                Algorithm hyperparameters (e.g. alpha, beta, gamma)
     * @return List of length horizonDays containing predicted daily customer demand
     */
    List<Double> forecast(List<Double> historicalDailyDemand, int horizonDays, Map<String, Object> params);

    default List<Double> forecast(List<Double> historicalDailyDemand, int horizonDays) {
        return forecast(historicalDailyDemand, horizonDays, java.util.Collections.emptyMap());
    }
}
