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
import com.billing.simple.billsoft.entities.Product;
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
    private final com.billing.simple.billsoft.repo.StockMovementRepository stockMovementRepo;
    private final com.billing.simple.billsoft.forecast.repository.ForecastRealizationLogRepository realizationLogRepo;

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

        Product prod = productRepo.findByIdAndFirmId(productId, firmId).orElse(null);
        long startTime = System.currentTimeMillis();

        // 1. Build contiguous daily net demand series from metrics with stockout timeline awareness
        List<ProductSalesMetrics> metrics = metricsRepo.findAllByFirmIdAndProductIdOrderByMetricDateAsc(firmId, productId);
        LocalDate firstDate = (metrics != null && !metrics.isEmpty()) ? metrics.get(0).getMetricDate() : LocalDate.now();
        LocalDate lastDate = LocalDate.now();
        List<Boolean> stockoutFlags = buildDailyStockoutTimeline(firmId, productId, firstDate, lastDate, metrics, prod);
        List<Double> dailyDemandSeries = buildContiguousDailyDemandSeries(metrics, stockoutFlags);

        // 2. Classify demand pattern with censored stockout observation handling
        DemandClassification classification = classifier.classify(dailyDemandSeries, stockoutFlags);

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
                backtest.getWape(), zeroDemandRate, classification.getCv2(), historyDays, backtest.isHasSufficientHistory());

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
                .hyperparametersJson("{\"alpha\":0.2,\"beta\":0.1,\"gamma\":0.2,\"phi\":0.90}")
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

        // 9. Persist realization tracking points across evaluation horizons
        if (realizationLogRepo != null) {
            for (int h : new int[]{1, 7, 14, 30, 60, 90}) {
                if (h <= horizon && h - 1 < dailyPredictions.size()) {
                    LocalDate targetDate = LocalDate.now().plusDays(h);
                    double pred = Math.max(0.0, dailyPredictions.get(h - 1));
                    com.billing.simple.billsoft.forecast.entity.ForecastRealizationLog logEntry =
                            com.billing.simple.billsoft.forecast.entity.ForecastRealizationLog.builder()
                                    .firmId(firmId)
                                    .productId(productId)
                                    .modelRunId(savedRun.getId())
                                    .originDate(LocalDate.now())
                                    .targetDate(targetDate)
                                    .horizonDays(h)
                                    .predictedDemand(BigDecimal.valueOf(pred).setScale(4, RoundingMode.HALF_UP))
                                    .modelName(model.getModelName())
                                    .evaluationStatus("PENDING")
                                    .build();
                    realizationLogRepo.save(logEntry);
                }
            }
        }

        log.info("Generated 365-day forecast for product {} in firm {} using {} (Confidence: {}/100, WAPE: {})",
                productId, firmId, model.getModelName(), confidence.getScore(), backtest.getWape());

        return Optional.of(savedRun);
    }

    private List<Double> buildContiguousDailyDemandSeries(List<ProductSalesMetrics> metrics) {
        return buildContiguousDailyDemandSeries(metrics, Collections.emptyList());
    }

    private List<Double> buildContiguousDailyDemandSeries(List<ProductSalesMetrics> metrics, List<Boolean> stockoutFlags) {
        if (metrics == null || metrics.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDate firstDate = metrics.get(0).getMetricDate();
        LocalDate lastDate = LocalDate.now();
        if (lastDate.isBefore(firstDate)) lastDate = firstDate;

        Map<LocalDate, Double> demandByDate = new HashMap<>();
        double totalDemand = 0.0;
        int nonZeroDays = 0;

        for (ProductSalesMetrics m : metrics) {
            double netDemand = m.getNetRequestedQty() != null
                    ? Math.max(0.0, m.getNetRequestedQty().doubleValue()) : 0.0;
            demandByDate.put(m.getMetricDate(), netDemand);
            if (netDemand > 0.0) {
                totalDemand += netDemand;
                nonZeroDays++;
            }
        }

        double inStockAvgDailyDemand = nonZeroDays > 0 ? (totalDemand / nonZeroDays) : 0.0;

        List<Double> series = new ArrayList<>();
        LocalDate curr = firstDate;
        int dayIdx = 0;
        int consecutiveStockoutDays = 0;

        while (!curr.isAfter(lastDate)) {
            boolean isStockout = (stockoutFlags != null && dayIdx < stockoutFlags.size() && Boolean.TRUE.equals(stockoutFlags.get(dayIdx)));
            Double recordedDemand = demandByDate.get(curr);

            if (recordedDemand != null && recordedDemand > 0.0) {
                series.add(recordedDemand);
                consecutiveStockoutDays = 0;
            } else if (isStockout) {
                consecutiveStockoutDays++;
                // Statistically defensible handling: for brief stockout gaps (<= 7 days) with proven historical demand,
                // impute baseline in-stock mean to prevent artificial trend collapse, while leaving extended stockouts at 0.0
                if (consecutiveStockoutDays <= 7 && inStockAvgDailyDemand > 0.0) {
                    series.add(inStockAvgDailyDemand);
                } else {
                    series.add(0.0);
                }
            } else {
                // Genuine zero-demand day
                series.add(0.0);
                consecutiveStockoutDays = 0;
            }

            curr = curr.plusDays(1);
            dayIdx++;
        }
        return series;
    }

    private List<Boolean> buildDailyStockoutTimeline(Long firmId, Long productId, LocalDate firstDate, LocalDate lastDate, List<ProductSalesMetrics> metrics, Product prod) {
        if (firstDate == null || lastDate == null || firstDate.isAfter(lastDate)) {
            return Collections.emptyList();
        }
        if (prod != null && "SERVICE".equalsIgnoreCase(prod.getItemType())) {
            int days = (int) java.time.temporal.ChronoUnit.DAYS.between(firstDate, lastDate) + 1;
            return new ArrayList<>(Collections.nCopies(days, false));
        }

        Map<LocalDate, Double> demandByDate = new HashMap<>();
        if (metrics != null) {
            for (ProductSalesMetrics m : metrics) {
                double net = m.getNetRequestedQty() != null ? m.getNetRequestedQty().doubleValue() : 0.0;
                if (net > 0.0) {
                    demandByDate.put(m.getMetricDate(), net);
                }
            }
        }

        List<com.billing.simple.billsoft.entities.StockMovement> movements = (stockMovementRepo != null && firmId != null && productId != null)
                ? stockMovementRepo.findByProductIdAndFirmIdOrderByCreatedAtDesc(productId, firmId)
                : Collections.emptyList();

        List<com.billing.simple.billsoft.entities.StockMovement> sortedMovs = new ArrayList<>(movements);
        sortedMovs.sort(Comparator.comparing(com.billing.simple.billsoft.entities.StockMovement::getCreatedAt));

        List<Boolean> stockoutFlags = new ArrayList<>();
        LocalDate curr = firstDate;
        int movIdx = 0;
        BigDecimal runningStock = BigDecimal.ZERO;
        if (!sortedMovs.isEmpty()) {
            runningStock = sortedMovs.get(0).getPreviousStock() != null ? sortedMovs.get(0).getPreviousStock() : BigDecimal.ZERO;
        } else if (prod != null && prod.getStockQuantity() != null) {
            runningStock = prod.getStockQuantity();
        }

        while (!curr.isAfter(lastDate)) {
            LocalDateTime endOfDay = curr.atTime(23, 59, 59);
            while (movIdx < sortedMovs.size() && !sortedMovs.get(movIdx).getCreatedAt().isAfter(endOfDay)) {
                runningStock = sortedMovs.get(movIdx).getNewStock() != null ? sortedMovs.get(movIdx).getNewStock() : BigDecimal.ZERO;
                movIdx++;
            }

            boolean hasObservedDemand = demandByDate.containsKey(curr);
            boolean isOutOfStock = runningStock.compareTo(BigDecimal.ZERO) <= 0;

            // If out of stock and no demand was recorded, this is a censored stockout observation
            boolean isCensoredStockout = isOutOfStock && !hasObservedDemand;
            stockoutFlags.add(isCensoredStockout);

            curr = curr.plusDays(1);
        }
        return stockoutFlags;
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
