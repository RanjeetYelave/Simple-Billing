package com.billing.simple.billsoft.forecast.backtest;

import com.billing.simple.billsoft.forecast.model.TimeSeriesDemandModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rolling-origin time-series cross-validation evaluator.
 * Evaluates WAPE, RMSE, and MAE across rolling temporal folds.
 */
@Component
public class RollingOriginBacktester {

    public BacktestResult evaluate(TimeSeriesDemandModel model, List<Double> historicalDailyDemand, Map<String, Object> params) {
        if (historicalDailyDemand == null || historicalDailyDemand.size() < 14) {
            return BacktestResult.builder()
                    .modelName(model.getModelName())
                    .wape(0.0)
                    .rmse(0.0)
                    .mae(0.0)
                    .foldsEvaluated(0)
                    .validationHorizonDays(0)
                    .build();
        }

        int n = historicalDailyDemand.size();
        int horizon;
        int numFolds;

        if (n < 42) {
            horizon = 7;
            numFolds = 1;
        } else {
            horizon = 14;
            numFolds = 3;
        }

        double totalAbsError = 0.0;
        double totalActualDemand = 0.0;
        double sumSquaredError = 0.0;
        int totalTestPoints = 0;
        int foldsCompleted = 0;

        for (int fold = 0; fold < numFolds; fold++) {
            // Rolling origin: fold 0 trains up to n - (numFolds - fold) * horizon
            int trainEndIdx = n - (numFolds - fold) * horizon;
            if (trainEndIdx < 7) continue;

            List<Double> trainData = historicalDailyDemand.subList(0, trainEndIdx);
            List<Double> testActuals = historicalDailyDemand.subList(trainEndIdx, trainEndIdx + horizon);

            List<Double> predictions = model.forecast(trainData, horizon, params);

            for (int t = 0; t < horizon; t++) {
                double actual = testActuals.get(t) != null ? Math.max(0.0, testActuals.get(t)) : 0.0;
                double pred = (predictions != null && t < predictions.size() && predictions.get(t) != null)
                        ? Math.max(0.0, predictions.get(t)) : 0.0;

                double diff = actual - pred;
                totalAbsError += Math.abs(diff);
                sumSquaredError += (diff * diff);
                totalActualDemand += actual;
                totalTestPoints++;
            }
            foldsCompleted++;
        }

        if (totalTestPoints == 0) {
            return BacktestResult.builder()
                    .modelName(model.getModelName())
                    .wape(0.0)
                    .rmse(0.0)
                    .mae(0.0)
                    .foldsEvaluated(0)
                    .validationHorizonDays(horizon)
                    .build();
        }

        double wape = totalActualDemand > 0.0 ? (totalAbsError / totalActualDemand) : (totalAbsError > 0 ? 1.0 : 0.0);
        double rmse = Math.sqrt(sumSquaredError / totalTestPoints);
        double mae = totalAbsError / totalTestPoints;

        return BacktestResult.builder()
                .modelName(model.getModelName())
                .wape(wape)
                .rmse(rmse)
                .mae(mae)
                .foldsEvaluated(foldsCompleted)
                .validationHorizonDays(horizon)
                .build();
    }
}
