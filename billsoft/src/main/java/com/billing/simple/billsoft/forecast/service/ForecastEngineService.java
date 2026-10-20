package com.billing.simple.billsoft.forecast.service;

import com.billing.simple.billsoft.forecast.backtest.BacktestResult;
import com.billing.simple.billsoft.forecast.backtest.RollingOriginBacktester;
import com.billing.simple.billsoft.forecast.confidence.ForecastConfidenceCalculator;
import com.billing.simple.billsoft.forecast.entity.ForecastModelRun;
import com.billing.simple.billsoft.forecast.entity.ForecastProduct;
import com.billing.simple.billsoft.forecast.entity.ProductDemandForecast;
import com.billing.simple.billsoft.forecast.entity.ProductSalesMetrics;
import com.billing.simple.billsoft.forecast.model.*;
import com.billing.simple.billsoft.forecast.repository.ForecastModelRunRepository;
import com.billing.simple.billsoft.forecast.repository.ProductDemandForecastRepository;
import com.billing.simple.billsoft.forecast.repository.ProductSalesMetricsRepository;
import com.billing.simple.billsoft.forecast.selection.DemandClassification;
import com.billing.simple.billsoft.forecast.selection.DemandPatternClassifier;
import com.billing.simple.billsoft.repo.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Executes the forecasting pipeline: demand classification, model selection, rolling-origin backtesting,
 * 365-day trajectory generation, confidence calculation, and audit logging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastEngineService {

    private final ProductSalesMetricsRepository metricsRepo;
    private final ForecastModelRunRepository modelRunRepo;
    private final ProductDemandForecastRepository forecastRepo;
    private final ProductRepository productRepo;
    private final ForecastProductConfigService configService;
    private final DemandPatternClassifier classifier;
    private final RollingOriginBacktester backtester;
    private final ForecastConfidenceCalculator confidenceCalculator;

    // Algorithm beans
    private final HistoricalMeanModel historicalMeanModel;
    private final SimpleExponentialSmoothingModel sesModel;
    private final HoltWintersModel holtWintersModel;
    private final CrostonModel crostonModel;
    private final SbaModel sbaModel;
    private final TsbModel tsbModel;

    @Transactional
    public Optional<ForecastModelRun> generateForecastForProduct(Long firmId, Long productId) {
        if (firmId == null || productId == null) return Optional.empty();

        ForecastProduct config = configService.getOrCreateConfig(firmId, productId);
        if (config.getForecastEnabled() != null && !config.getForecastEnabled()) {
            log.debug("Forecasting is disabled for product {} in firm {}", productId, firmId);
            return Optional.empty();
        }

        long startTime = System.currentTimeMillis();

        // 1. Build contiguous daily net demand series from metrics
        List<ProductSalesMetrics> metrics = metricsRepo.findAllByFirmIdAndProductIdOrderByMetricDateAsc(firmId, productId);
        List<Double> dailyDemandSeries = buildContiguousDailyDemandSeries(metrics);

        // 2. Classify demand pattern
        DemandClassification classification = classifier.classify(dailyDemandSeries);

        // 3. Select algorithm
        TimeSeriesDemandModel model = selectModel(classification.getRecommendedModel(), config.getModelOverride());

        // 4. Execute rolling-origin backtest
        Map<String, Object> params = Collections.emptyMap();
        BacktestResult backtest = backtester.evaluate(model, dailyDemandSeries, params);

        // 5. Calculate confidence score
        int historyDays = dailyDemandSeries.size();
        double zeroCount = 0;
        for (Double d : dailyDemandSeries) {
            if (d == null || d <= 0.0) zeroCount++;
        }
        double zeroDemandRate = historyDays > 0 ? (zeroCount / historyDays) : 1.0;
        ForecastConfidenceCalculator.ConfidenceResult confidence = confidenceCalculator.calculate(
                backtest.getWape(), zeroDemandRate, classification.getCv2(), historyDays);

        // 6. Generate 365-day forecast trajectory
        int horizon = 365;
        List<Double> dailyPredictions = model.forecast(dailyDemandSeries, horizon, params);

        long durationMs = System.currentTimeMillis() - startTime;

        // 7. Persist immutable ForecastModelRun audit log
        ForecastModelRun run = ForecastModelRun.builder()
                .firmId(firmId)
                .productId(productId)
                .runTimestamp(LocalDateTime.now())
                .modelName(model.getModelName())
                .modelVersion(model.getModelVersion())
                .hyperparametersJson("{\"alpha\":0.2,\"beta\":0.1,\"gamma\":0.2}")
                .demandPattern(classification.getPattern())
                .adi(BigDecimal.valueOf(classification.getAdi()).setScale(4, RoundingMode.HALF_UP))
                .cv2(BigDecimal.valueOf(classification.getCv2()).setScale(4, RoundingMode.HALF_UP))
                .wape(BigDecimal.valueOf(backtest.getWape()).setScale(4, RoundingMode.HALF_UP))
                .rmse(BigDecimal.valueOf(backtest.getRmse()).setScale(4, RoundingMode.HALF_UP))
                .mae(BigDecimal.valueOf(backtest.getMae()).setScale(4, RoundingMode.HALF_UP))
                .trainingDays(historyDays)
                .backtestFolds(backtest.getFoldsEvaluated())
                .executionDurationMs(durationMs)
                .status("SUCCESS")
                .statusMessage("Model executed successfully")
                .build();

        ForecastModelRun savedRun = modelRunRepo.save(run);

        // 8. Persist daily predicted trajectory points
        forecastRepo.deleteAllByFirmIdAndProductId(firmId, productId);
        LocalDate startDate = LocalDate.now().plusDays(1);
        List<ProductDemandForecast> forecastPoints = new ArrayList<>(horizon);

        for (int i = 0; i < horizon; i++) {
            LocalDate fDate = startDate.plusDays(i);
            double pred = (dailyPredictions != null && i < dailyPredictions.size() && dailyPredictions.get(i) != null)
                    ? Math.max(0.0, dailyPredictions.get(i)) : 0.0;

            ProductDemandForecast pt = ProductDemandForecast.builder()
                    .firmId(firmId)
                    .productId(productId)
                    .modelRunId(savedRun.getId())
                    .forecastDate(fDate)
                    .predictedDemand(BigDecimal.valueOf(pred).setScale(4, RoundingMode.HALF_UP))
                    .confidenceScore(confidence.getScore())
                    .confidenceLevel(confidence.getLevel())
                    .createdAt(LocalDateTime.now())
                    .build();
            forecastPoints.add(pt);
        }
        forecastRepo.saveAll(forecastPoints);

        log.info("Generated 365-day forecast for product {} in firm {} using {} (Confidence: {}/100, WAPE: {})",
                productId, firmId, model.getModelName(), confidence.getScore(), backtest.getWape());

        return Optional.of(savedRun);
    }

    private List<Double> buildContiguousDailyDemandSeries(List<ProductSalesMetrics> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDate firstDate = metrics.get(0).getMetricDate();
        LocalDate lastDate = LocalDate.now();
        if (lastDate.isBefore(firstDate)) lastDate = firstDate;

        Map<LocalDate, Double> demandByDate = new HashMap<>();
        for (ProductSalesMetrics m : metrics) {
            double netDemand = m.getNetRequestedQty() != null
                    ? Math.max(0.0, m.getNetRequestedQty().doubleValue()) : 0.0;
            demandByDate.put(m.getMetricDate(), netDemand);
        }

        List<Double> series = new ArrayList<>();
        LocalDate curr = firstDate;
        while (!curr.isAfter(lastDate)) {
            series.add(demandByDate.getOrDefault(curr, 0.0));
            curr = curr.plusDays(1);
        }
        return series;
    }

    private TimeSeriesDemandModel selectModel(String recommendedModel, String modelOverride) {
        String target = (modelOverride != null && !modelOverride.isBlank()) ? modelOverride : recommendedModel;
        if (target == null) return historicalMeanModel;

        return switch (target.toUpperCase().trim()) {
            case "CROSTON" -> crostonModel;
            case "SBA" -> sbaModel;
            case "TSB" -> tsbModel;
            case "SES" -> sesModel;
            case "HOLT_WINTERS", "HOLTWINTERS" -> holtWintersModel;
            default -> historicalMeanModel;
        };
    }

    @Transactional
    public int generateCatalogForecasts(Long firmId) {
        List<com.billing.simple.billsoft.entities.Product> products = productRepo.findByFirmIdAndInInventoryTrueAndIsArchivedFalseOrderByNameAsc(firmId);
        int count = 0;
        for (com.billing.simple.billsoft.entities.Product p : products) {
            Long pId = p.getId();
            try {
                if (generateForecastForProduct(firmId, pId).isPresent()) {
                    count++;
                }
            } catch (Exception e) {
                log.error("Failed to generate forecast for product {} in firm {}: {}", pId, firmId, e.getMessage(), e);
            }
        }
        log.info("Completed catalog forecast generation for {} products in firm {}", count, firmId);
        return count;
    }

    @Transactional
    public int generateForecastsForCatalog(Long firmId) {
        return generateCatalogForecasts(firmId);
    }
}
