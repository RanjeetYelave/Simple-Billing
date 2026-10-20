package com.billing.simple.billsoft.forecast.service;

import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderItem;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import com.billing.simple.billsoft.forecast.config.ForecastModuleProperties;
import com.billing.simple.billsoft.forecast.dto.*;
import com.billing.simple.billsoft.forecast.entity.*;
import com.billing.simple.billsoft.forecast.repository.*;
import com.billing.simple.billsoft.repo.ProductRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Calculates advisory replenishment recommendations, expected stock-out dates,
 * and compiles comprehensive product intelligence insights.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestockAdvisoryService {

    private final ProductRepository productRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final ForecastProductConfigService configService;
    private final ProductDemandForecastRepository forecastRepo;
    private final ForecastModelRunRepository modelRunRepo;
    private final ProductRestockRecommendationRepository restockRepo;
    private final ProductSalesMetricsRepository metricsRepo;
    private final ForecastSalesTransactionRepository transactionRepo;
    private final ForecastModuleProperties properties;

    @Transactional
    public ProductRestockRecommendation calculateRecommendation(Long firmId, Long productId, Long modelRunId) {
        Product product = productRepo.findByIdAndFirmId(productId, firmId).orElse(null);
        if (product == null) {
            return null;
        }

        // Group 6 Invariants: Archived products must remain excluded from Smart Reorder
        if (Boolean.TRUE.equals(product.getIsArchived())) {
            restockRepo.findTopByFirmIdAndProductIdAndRecommendationStatusOrderByGeneratedAtDesc(firmId, productId, "ACTIVE")
                    .ifPresent(existing -> {
                        existing.setRecommendationStatus("SUPERSEDED");
                        restockRepo.save(existing);
                    });
            return null;
        }

        // Group 6 Invariants: Canonical merged products must continue using canonical identity
        if (product.getCanonicalProductId() != null) {
            restockRepo.findTopByFirmIdAndProductIdAndRecommendationStatusOrderByGeneratedAtDesc(firmId, productId, "ACTIVE")
                    .ifPresent(existing -> {
                        existing.setRecommendationStatus("SUPERSEDED");
                        restockRepo.save(existing);
                    });
            return calculateRecommendation(firmId, product.getCanonicalProductId(), modelRunId);
        }

        ForecastProduct config = configService.getOrCreateConfig(firmId, productId);

        int leadTime = config.getLeadTimeDays() != null ? config.getLeadTimeDays() : properties.getDefaultLeadTimeDays();
        int reviewPeriod = config.getReviewPeriodDays() != null ? config.getReviewPeriodDays() : properties.getDefaultReviewPeriodDays();
        int planningHorizon = leadTime + reviewPeriod;

        BigDecimal moq = config.getMinimumOrderQty() != null ? config.getMinimumOrderQty() : properties.getDefaultMoq();
        BigDecimal packSize = config.getPackSize() != null ? config.getPackSize() : properties.getDefaultPackSize();

        // 1. Current physical inventory
        BigDecimal currentStock = (product != null && product.getStockQuantity() != null)
                ? product.getStockQuantity() : BigDecimal.ZERO;

        // 2. Incoming stock from ISSUED Purchase Orders
        BigDecimal incomingStock = calculateIncomingIssuedPoStock(firmId, productId);
        BigDecimal netInventoryPosition = currentStock.add(incomingStock);

        // 3. Forecast demand trajectory over planning horizon
        List<ProductDemandForecast> forecasts = (modelRunId != null)
                ? forecastRepo.findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(firmId, productId, modelRunId)
                : Collections.emptyList();

        BigDecimal leadTimeDemand = BigDecimal.ZERO;
        BigDecimal reviewPeriodDemand = BigDecimal.ZERO;

        for (int i = 0; i < forecasts.size(); i++) {
            BigDecimal dayDemand = forecasts.get(i).getPredictedDemand() != null
                    ? forecasts.get(i).getPredictedDemand() : BigDecimal.ZERO;
            if (i < leadTime) {
                leadTimeDemand = leadTimeDemand.add(dayDemand);
            } else if (i < planningHorizon) {
                reviewPeriodDemand = reviewPeriodDemand.add(dayDemand);
            }
        }
        BigDecimal totalHorizonDemand = leadTimeDemand.add(reviewPeriodDemand);

        // 4. Calculate Safety Stock
        ForecastModelRun modelRun = (modelRunId != null) ? modelRunRepo.findById(modelRunId).orElse(null) : null;
        BigDecimal safetyStock;
        if (config.getSafetyStockOverride() != null) {
            safetyStock = config.getSafetyStockOverride().max(BigDecimal.ZERO);
        } else if (modelRun != null && modelRun.getTrainingDays() >= 14 && modelRun.getRmse() != null) {
            double z = properties.getDefaultSafetyStockZ();
            double rmse = modelRun.getRmse().doubleValue();
            double calcSafety = z * rmse * Math.sqrt(leadTime);
            safetyStock = BigDecimal.valueOf(calcSafety).setScale(3, RoundingMode.HALF_UP);
        } else {
            // Fallback safety stock for short history
            double avgDaily = leadTime > 0 ? (leadTimeDemand.doubleValue() / leadTime) : 0.0;
            safetyStock = BigDecimal.valueOf(0.5 * avgDaily * leadTime).setScale(3, RoundingMode.HALF_UP);
        }

        // 5. Target stock & Raw Forecast Shortfall
        BigDecimal targetStock = totalHorizonDemand.setScale(3, RoundingMode.HALF_UP).add(safetyStock);
        BigDecimal rawForecastShortfall = targetStock.subtract(netInventoryPosition).max(BigDecimal.ZERO);

        // 6. User-Configured Minimum Stock Threshold Signal
        BigDecimal minStockLevel = (product != null && product.getMinStockLevel() != null)
                ? product.getMinStockLevel() : BigDecimal.ZERO;
        BigDecimal rawThresholdShortfall = minStockLevel.subtract(netInventoryPosition).max(BigDecimal.ZERO);

        // 7. Combined Unconstrained Requirement
        BigDecimal rawShortfall = rawForecastShortfall.max(rawThresholdShortfall);

        // 8. Apply MOQ Floor
        BigDecimal moqAdjusted = rawShortfall;
        if (rawShortfall.compareTo(BigDecimal.ZERO) > 0 && rawShortfall.compareTo(moq) < 0) {
            moqAdjusted = moq;
        }

        // 9. Apply Pack Size Upward Ceiling Rounding
        BigDecimal finalRecommendedQty = moqAdjusted;
        if (moqAdjusted.compareTo(BigDecimal.ZERO) > 0 && packSize.compareTo(BigDecimal.ONE) > 0) {
            BigDecimal packs = moqAdjusted.divide(packSize, 0, RoundingMode.CEILING);
            finalRecommendedQty = packs.multiply(packSize);
        }

        // 10. Expected stock-out date projection
        LocalDate stockoutDate = calculateExpectedStockoutDate(currentStock, incomingStock, forecasts);

        // 11. Urgency determination
        String urgency;
        if (currentStock.compareTo(BigDecimal.ZERO) <= 0) {
            urgency = "OUT_OF_STOCK";
        } else if (stockoutDate != null && !stockoutDate.isAfter(LocalDate.now().plusDays(leadTime))) {
            urgency = "CRITICAL";
        } else if (finalRecommendedQty.compareTo(BigDecimal.ZERO) > 0) {
            urgency = "REORDER_NEEDED";
        } else {
            urgency = "HEALTHY";
        }

        String confidenceLevel = (modelRun != null && !forecasts.isEmpty())
                ? forecasts.get(0).getConfidenceLevel() : "LOW";

        String reasonStr;
        if (rawForecastShortfall.compareTo(BigDecimal.ZERO) > 0 && rawThresholdShortfall.compareTo(BigDecimal.ZERO) == 0) {
            reasonStr = "Forecast";
        } else if (rawThresholdShortfall.compareTo(BigDecimal.ZERO) > 0 && rawForecastShortfall.compareTo(BigDecimal.ZERO) == 0) {
            reasonStr = "Minimum Stock";
        } else if (rawForecastShortfall.compareTo(rawThresholdShortfall) > 0) {
            reasonStr = "Forecast";
        } else if (rawThresholdShortfall.compareTo(rawForecastShortfall) > 0) {
            reasonStr = "Minimum Stock";
        } else if (rawForecastShortfall.compareTo(BigDecimal.ZERO) > 0) {
            reasonStr = "Forecast + Minimum Stock";
        } else {
            reasonStr = "Healthy";
        }

        // 12. Formulate explainability text
        String explanation = String.format(
                "Forecast recommendation: %s units (Horizon demand: %s, Safety stock: %s). " +
                "Minimum stock requirement: %s units (Configured min: %s). " +
                "Current stock: %s, Incoming PO: %s (Net Position: %s). " +
                "Primary driver: %s. Final recommended order: %s units%s%s.",
                rawForecastShortfall.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                totalHorizonDemand.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                safetyStock.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                rawThresholdShortfall.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                minStockLevel.stripTrailingZeros().toPlainString(),
                currentStock.stripTrailingZeros().toPlainString(),
                incomingStock.stripTrailingZeros().toPlainString(),
                netInventoryPosition.stripTrailingZeros().toPlainString(),
                reasonStr,
                finalRecommendedQty.stripTrailingZeros().toPlainString(),
                (rawShortfall.compareTo(BigDecimal.ZERO) > 0 && rawShortfall.compareTo(moq) < 0) ? String.format(" (Raised to MOQ %s)", moq.stripTrailingZeros().toPlainString()) : "",
                (finalRecommendedQty.compareTo(moqAdjusted) > 0) ? String.format(" (Rounded to pack multiple %s)", packSize.stripTrailingZeros().toPlainString()) : ""
        );

        // Mark existing active recommendations as SUPERSEDED
        restockRepo.findTopByFirmIdAndProductIdAndRecommendationStatusOrderByGeneratedAtDesc(firmId, productId, "ACTIVE")
                .ifPresent(existing -> {
                    existing.setRecommendationStatus("SUPERSEDED");
                    restockRepo.save(existing);
                });

        ProductRestockRecommendation rec = ProductRestockRecommendation.builder()
                .firmId(firmId)
                .productId(productId)
                .modelRunId(modelRunId != null ? modelRunId : 0L)
                .currentStock(currentStock)
                .incomingStock(incomingStock)
                .leadTimeDemand(leadTimeDemand)
                .reviewPeriodDemand(reviewPeriodDemand)
                .totalHorizonDemand(totalHorizonDemand)
                .safetyStock(safetyStock)
                .targetStock(targetStock)
                .rawRecommendation(rawShortfall)
                .moqAdjustedRecommendation(moqAdjusted)
                .recommendedOrderQty(finalRecommendedQty)
                .expectedStockoutDate(stockoutDate)
                .urgencyStatus(urgency)
                .confidenceLevel(confidenceLevel)
                .recommendationStatus("ACTIVE")
                .userActionState("PENDING")
                .explanationText(explanation)
                .generatedAt(LocalDateTime.now())
                .build();

        return restockRepo.save(rec);
    }

    private BigDecimal calculateIncomingIssuedPoStock(Long firmId, Long productId) {
        List<PurchaseOrder> issuedPos = purchaseOrderRepo.findByFirmIdAndStatusOrderByPoDateDescIdDesc(
                firmId, PurchaseOrderStatus.ISSUED);
        BigDecimal sum = BigDecimal.ZERO;
        for (PurchaseOrder po : issuedPos) {
            if (po.getItems() != null) {
                for (PurchaseOrderItem it : po.getItems()) {
                    if (it.getProductId() != null && it.getProductId().equals(productId)) {
                        BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
                        sum = sum.add(qty);
                    }
                }
            }
        }
        return sum;
    }

    private LocalDate calculateExpectedStockoutDate(BigDecimal currentStock, BigDecimal incomingStock, List<ProductDemandForecast> forecasts) {
        if (currentStock.compareTo(BigDecimal.ZERO) <= 0) {
            return LocalDate.now();
        }
        if (forecasts == null || forecasts.isEmpty()) {
            return null;
        }

        BigDecimal available = currentStock.add(incomingStock);
        BigDecimal cumDemand = BigDecimal.ZERO;

        for (ProductDemandForecast pt : forecasts) {
            BigDecimal d = pt.getPredictedDemand() != null ? pt.getPredictedDemand() : BigDecimal.ZERO;
            cumDemand = cumDemand.add(d);
            if (cumDemand.compareTo(available) >= 0) {
                return pt.getForecastDate();
            }
        }
        return null; // Stockout beyond 365 days
    }

    @Transactional
    public int generateCatalogRecommendations(Long firmId) {
        List<Product> products = productRepo.findByFirmIdAndInInventoryTrueAndIsArchivedFalseOrderByNameAsc(firmId);
        int count = 0;
        for (Product p : products) {
            Optional<ForecastModelRun> lastRun = modelRunRepo.findTopByFirmIdAndProductIdOrderByRunTimestampDesc(firmId, p.getId());
            Long runId = lastRun.map(ForecastModelRun::getId).orElse(null);
            calculateRecommendation(firmId, p.getId(), runId);
            count++;
        }
        log.info("Generated replenishment recommendations for {} products in firm {}", count, firmId);
        return count;
    }

    @Transactional
    public void refreshRecommendationsForProducts(Long firmId, Collection<Long> productIds) {
        if (firmId == null || productIds == null || productIds.isEmpty()) return;
        for (Long pid : productIds) {
            if (pid == null) continue;
            Optional<Product> prodOpt = productRepo.findByIdAndFirmId(pid, firmId);
            if (prodOpt.isEmpty()) continue;
            Product p = prodOpt.get();

            if (Boolean.TRUE.equals(p.getIsArchived())) {
                restockRepo.findTopByFirmIdAndProductIdAndRecommendationStatusOrderByGeneratedAtDesc(firmId, pid, "ACTIVE")
                        .ifPresent(existing -> {
                            existing.setRecommendationStatus("SUPERSEDED");
                            restockRepo.save(existing);
                        });
                continue;
            }

            Long targetProductId = pid;
            if (p.getCanonicalProductId() != null) {
                restockRepo.findTopByFirmIdAndProductIdAndRecommendationStatusOrderByGeneratedAtDesc(firmId, pid, "ACTIVE")
                        .ifPresent(existing -> {
                            existing.setRecommendationStatus("SUPERSEDED");
                            restockRepo.save(existing);
                        });
                targetProductId = p.getCanonicalProductId();
            }

            Optional<ForecastModelRun> lastRun = modelRunRepo.findTopByFirmIdAndProductIdOrderByRunTimestampDesc(firmId, targetProductId);
            Long runId = lastRun.map(ForecastModelRun::getId).orElse(null);
            calculateRecommendation(firmId, targetProductId, runId);
        }
    }

    @Transactional(readOnly = true)
    public ProductInsightResponse getInsights(Long firmId, Long productId) {
        Product p = productRepo.findByIdAndFirmId(productId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        List<Object[]> aggs = metricsRepo.getHistoricalInsightSummaryForProduct(firmId, productId);
        BigDecimal grossUnits = BigDecimal.ZERO;
        BigDecimal fulfilledUnits = BigDecimal.ZERO;
        BigDecimal shortageUnits = BigDecimal.ZERO;
        BigDecimal returnedUnits = BigDecimal.ZERO;
        BigDecimal netUnits = BigDecimal.ZERO;
        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal refunds = BigDecimal.ZERO;
        BigDecimal netSales = BigDecimal.ZERO;
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        LocalDate lastSold = null;

        if (aggs != null && !aggs.isEmpty() && aggs.get(0) != null) {
            Object[] r = aggs.get(0);
            if (r[0] != null) grossUnits = (BigDecimal) r[0];
            if (r[1] != null) fulfilledUnits = (BigDecimal) r[1];
            if (r[2] != null) shortageUnits = (BigDecimal) r[2];
            if (r[3] != null) returnedUnits = (BigDecimal) r[3];
            if (r[4] != null) netUnits = (BigDecimal) r[4];
            if (r[5] != null) grossSales = (BigDecimal) r[5];
            if (r[6] != null) refunds = (BigDecimal) r[6];
            if (r[7] != null) netSales = (BigDecimal) r[7];
            if (r[8] != null) minPrice = (BigDecimal) r[8];
            if (r[9] != null) maxPrice = (BigDecimal) r[9];
            if (r[10] != null) lastSold = (LocalDate) r[10];
        }

        BigDecimal wasp = null;
        if (grossUnits.compareTo(BigDecimal.ZERO) > 0) {
            wasp = grossSales.divide(grossUnits, 2, RoundingMode.HALF_UP);
        }

        Integer uniqueCustomers = metricsRepo.countDistinctLifetimeCustomersForProduct(firmId, productId);

        // Forecast horizons
        Optional<ForecastModelRun> latestRun = modelRunRepo.findTopByFirmIdAndProductIdOrderByRunTimestampDesc(firmId, productId);
        BigDecimal f30 = BigDecimal.ZERO;
        BigDecimal f90 = BigDecimal.ZERO;
        BigDecimal f180 = BigDecimal.ZERO;
        BigDecimal f365 = BigDecimal.ZERO;
        String confidenceLevel = "LOW";
        BigDecimal confidenceScore = BigDecimal.ZERO;

        if (latestRun.isPresent()) {
            List<ProductDemandForecast> pts = forecastRepo.findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(
                    firmId, productId, latestRun.get().getId());
            if (!pts.isEmpty()) {
                confidenceLevel = pts.get(0).getConfidenceLevel();
                confidenceScore = pts.get(0).getConfidenceScore();
                for (int i = 0; i < pts.size(); i++) {
                    BigDecimal d = pts.get(i).getPredictedDemand() != null ? pts.get(i).getPredictedDemand() : BigDecimal.ZERO;
                    if (i < 30) f30 = f30.add(d);
                    if (i < 90) f90 = f90.add(d);
                    if (i < 180) f180 = f180.add(d);
                    if (i < 365) f365 = f365.add(d);
                }
            }
        }

        Optional<ProductRestockRecommendation> latestRec = restockRepo.findTopByFirmIdAndProductIdAndRecommendationStatusOrderByGeneratedAtDesc(
                firmId, productId, "ACTIVE");

        BigDecimal coverageDays = null;
        if (f30.compareTo(BigDecimal.ZERO) > 0) {
            double dailyBurn = f30.doubleValue() / 30.0;
            double current = p.getStockQuantity() != null ? p.getStockQuantity().doubleValue() : 0.0;
            coverageDays = BigDecimal.valueOf(current / dailyBurn).setScale(1, RoundingMode.HALF_UP);
        }

        return ProductInsightResponse.builder()
                .productId(p.getId())
                .productName(p.getName())
                .sku(p.getSku())
                .category(p.getCategory())
                .itemType(p.getItemType())
                .unit(p.getUnit())
                .currentPrice(p.getPrice())
                .currentStock(p.getStockQuantity() != null ? p.getStockQuantity() : BigDecimal.ZERO)
                .incomingStock(calculateIncomingIssuedPoStock(firmId, productId))
                .grossUnitsSold(grossUnits)
                .fulfilledUnitsSold(fulfilledUnits)
                .shortageUnits(shortageUnits)
                .returnedUnits(returnedUnits)
                .netUnitsSold(netUnits)
                .grossSales(grossSales)
                .refunds(refunds)
                .netSales(netSales)
                .weightedAverageSellingPrice(wasp)
                .minRealizedPrice(minPrice)
                .maxRealizedPrice(maxPrice)
                .uniqueCustomerCount(uniqueCustomers != null ? uniqueCustomers : 0)
                .lastSoldDate(lastSold)
                .forecast30Days(f30.setScale(2, RoundingMode.HALF_UP))
                .forecast90Days(f90.setScale(2, RoundingMode.HALF_UP))
                .forecast180Days(f180.setScale(2, RoundingMode.HALF_UP))
                .forecast365Days(f365.setScale(2, RoundingMode.HALF_UP))
                .stockCoverageDays(coverageDays)
                .expectedStockoutDate(latestRec.map(ProductRestockRecommendation::getExpectedStockoutDate).orElse(null))
                .recommendedOrderQty(latestRec.map(ProductRestockRecommendation::getRecommendedOrderQty).orElse(BigDecimal.ZERO))
                .restockUrgency(latestRec.map(ProductRestockRecommendation::getUrgencyStatus).orElse("HEALTHY"))
                .forecastConfidenceLevel(confidenceLevel)
                .forecastConfidenceScore(confidenceScore)
                .build();
    }

    @Transactional(readOnly = true)
    public HistoricalTrendsResponse getTrends(Long firmId, Long productId, String range) {
        productRepo.findByIdAndFirmId(productId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        int days = switch (range != null ? range.toLowerCase().trim() : "90d") {
            case "30d" -> 30;
            case "180d" -> 180;
            case "365d", "1y" -> 365;
            default -> 90;
        };

        LocalDate startDate = LocalDate.now().minusDays(days);
        LocalDate endDate = LocalDate.now();

        List<ProductSalesMetrics> metrics = metricsRepo.findAllByFirmIdAndProductIdAndMetricDateBetweenOrderByMetricDateAsc(
                firmId, productId, startDate, endDate);

        List<HistoricalTrendsResponse.TrendPoint> points = metrics.stream()
                .map(m -> HistoricalTrendsResponse.TrendPoint.builder()
                        .date(m.getMetricDate())
                        .grossDemand(m.getGrossRequestedQty())
                        .fulfilledDemand(m.getGrossFulfilledQty())
                        .returnedDemand(m.getReturnedQty())
                        .netDemand(m.getNetRequestedQty())
                        .grossSales(m.getGrossSales())
                        .netSales(m.getNetSales())
                        .realizedUnitPrice(m.getTransactionTimeRealizedPrice())
                        .build())
                .collect(Collectors.toList());

        return HistoricalTrendsResponse.builder()
                .productId(productId)
                .range(range)
                .points(points)
                .build();
    }

    @Transactional(readOnly = true)
    public ForecastTrajectoryResponse getTrajectory(Long firmId, Long productId) {
        productRepo.findByIdAndFirmId(productId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        Optional<ForecastModelRun> latestRun = modelRunRepo.findTopByFirmIdAndProductIdOrderByRunTimestampDesc(firmId, productId);
        if (latestRun.isEmpty()) {
            return ForecastTrajectoryResponse.builder()
                    .productId(productId)
                    .selectedModel("NONE")
                    .confidenceScore(BigDecimal.ZERO)
                    .confidenceLevel("LOW")
                    .trajectory(Collections.emptyList())
                    .build();
        }

        ForecastModelRun run = latestRun.get();
        List<ProductDemandForecast> pts = forecastRepo.findAllByFirmIdAndProductIdAndModelRunIdOrderByForecastDateAsc(
                firmId, productId, run.getId());

        BigDecimal f30 = BigDecimal.ZERO;
        BigDecimal f90 = BigDecimal.ZERO;
        BigDecimal f180 = BigDecimal.ZERO;
        BigDecimal f365 = BigDecimal.ZERO;

        for (int i = 0; i < pts.size(); i++) {
            BigDecimal d = pts.get(i).getPredictedDemand() != null ? pts.get(i).getPredictedDemand() : BigDecimal.ZERO;
            if (i < 30) f30 = f30.add(d);
            if (i < 90) f90 = f90.add(d);
            if (i < 180) f180 = f180.add(d);
            if (i < 365) f365 = f365.add(d);
        }

        List<ForecastTrajectoryResponse.DailyForecastPoint> traj = pts.stream()
                .map(p -> ForecastTrajectoryResponse.DailyForecastPoint.builder()
                        .date(p.getForecastDate())
                        .predictedDemand(p.getPredictedDemand())
                        .build())
                .collect(Collectors.toList());

        return ForecastTrajectoryResponse.builder()
                .productId(productId)
                .modelRunId(run.getId())
                .selectedModel(run.getModelName())
                .demandPattern(run.getDemandPattern())
                .confidenceScore(pts.isEmpty() ? BigDecimal.ZERO : pts.get(0).getConfidenceScore())
                .confidenceLevel(pts.isEmpty() ? "LOW" : pts.get(0).getConfidenceLevel())
                .generatedAt(run.getRunTimestamp())
                .horizon30dDemand(f30.setScale(2, RoundingMode.HALF_UP))
                .horizon90dDemand(f90.setScale(2, RoundingMode.HALF_UP))
                .horizon180dDemand(f180.setScale(2, RoundingMode.HALF_UP))
                .horizon365dDemand(f365.setScale(2, RoundingMode.HALF_UP))
                .trajectory(traj)
                .build();
    }

    @Transactional(readOnly = true)
    public RestockExplanationDto getExplanation(Long firmId, Long productId) {
        Product p = productRepo.findByIdAndFirmId(productId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        ForecastProduct config = configService.getOrCreateConfig(firmId, productId);
        Optional<ProductRestockRecommendation> latestRec = restockRepo.findTopByFirmIdAndProductIdAndRecommendationStatusOrderByGeneratedAtDesc(
                firmId, productId, "ACTIVE");

        Optional<ForecastModelRun> latestRun = modelRunRepo.findTopByFirmIdAndProductIdOrderByRunTimestampDesc(firmId, productId);

        int lt = config.getLeadTimeDays() != null ? config.getLeadTimeDays() : properties.getDefaultLeadTimeDays();
        int rp = config.getReviewPeriodDays() != null ? config.getReviewPeriodDays() : properties.getDefaultReviewPeriodDays();

        if (latestRec.isEmpty()) {
            return RestockExplanationDto.builder()
                    .productId(productId)
                    .productName(p.getName())
                    .sku(p.getSku())
                    .leadTimeDays(lt)
                    .reviewPeriodDays(rp)
                    .planningHorizonDays(lt + rp)
                    .currentStock(p.getStockQuantity() != null ? p.getStockQuantity() : BigDecimal.ZERO)
                    .incomingIssuedPoStock(calculateIncomingIssuedPoStock(firmId, productId))
                    .finalRecommendedOrderQty(BigDecimal.ZERO)
                    .urgencyStatus("HEALTHY")
                    .confidenceLevel("LOW")
                    .explanationText("No recommendation generated yet.")
                    .build();
        }

        ProductRestockRecommendation r = latestRec.get();
        return RestockExplanationDto.builder()
                .productId(productId)
                .productName(p.getName())
                .sku(p.getSku())
                .leadTimeDays(lt)
                .reviewPeriodDays(rp)
                .planningHorizonDays(lt + rp)
                .minimumOrderQty(config.getMinimumOrderQty())
                .packSize(config.getPackSize())
                .currentStock(r.getCurrentStock())
                .incomingIssuedPoStock(r.getIncomingStock())
                .netInventoryPosition(r.getCurrentStock().add(r.getIncomingStock()))
                .leadTimeDemand(r.getLeadTimeDemand())
                .reviewPeriodDemand(r.getReviewPeriodDemand())
                .totalPlanningHorizonDemand(r.getTotalHorizonDemand())
                .safetyStock(r.getSafetyStock())
                .safetyStockOverridden(config.getSafetyStockOverride() != null)
                .targetStockLevel(r.getTargetStock())
                .rawShortfall(r.getRawRecommendation())
                .moqAdjusted(r.getMoqAdjustedRecommendation())
                .finalRecommendedOrderQty(r.getRecommendedOrderQty())
                .expectedStockoutDate(r.getExpectedStockoutDate())
                .urgencyStatus(r.getUrgencyStatus())
                .confidenceLevel(r.getConfidenceLevel())
                .confidenceScore(latestRun.map(ForecastModelRun::getWape).orElse(null))
                .selectedModel(latestRun.map(ForecastModelRun::getModelName).orElse("NONE"))
                .explanationText(r.getExplanationText())
                .generatedAt(r.getGeneratedAt())
                .build();
    }

    public static String deriveRecommendationReason(BigDecimal forecastReq, BigDecimal thresholdReq) {
        BigDecimal f = forecastReq != null ? forecastReq : BigDecimal.ZERO;
        BigDecimal t = thresholdReq != null ? thresholdReq : BigDecimal.ZERO;
        if (f.compareTo(BigDecimal.ZERO) > 0 && t.compareTo(BigDecimal.ZERO) == 0) return "FORECAST";
        if (t.compareTo(BigDecimal.ZERO) > 0 && f.compareTo(BigDecimal.ZERO) == 0) return "MINIMUM_STOCK";
        if (f.compareTo(t) > 0) return "FORECAST";
        if (t.compareTo(f) > 0) return "MINIMUM_STOCK";
        if (f.compareTo(BigDecimal.ZERO) > 0) return "BOTH";
        return "HEALTHY";
    }

    @Transactional(readOnly = true)
    public Page<RestockRecommendationDto> getActiveRecommendations(Long firmId, String urgencyStatus, Pageable pageable) {
        Page<ProductRestockRecommendation> page;
        if (urgencyStatus != null && !urgencyStatus.isBlank() && !"ALL".equalsIgnoreCase(urgencyStatus)) {
            page = restockRepo.findAllByFirmIdAndRecommendationStatusAndUrgencyStatus(
                    firmId, "ACTIVE", urgencyStatus.toUpperCase().trim(), pageable);
        } else {
            page = restockRepo.findAllByFirmIdAndRecommendationStatus(firmId, "ACTIVE", pageable);
        }

        List<Long> productIds = page.getContent().stream().map(ProductRestockRecommendation::getProductId).toList();
        Map<Long, Product> prodMap = productRepo.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, pr -> pr));

        List<RestockRecommendationDto> dtos = page.getContent().stream().map(r -> {
            Product p = prodMap.get(r.getProductId());
            BigDecimal unitCost = (p != null && p.getCostPrice() != null) ? p.getCostPrice() : (p != null ? p.getPrice() : BigDecimal.ZERO);
            BigDecimal estCost = (unitCost != null && r.getRecommendedOrderQty() != null)
                    ? unitCost.multiply(r.getRecommendedOrderQty()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            BigDecimal minStock = (p != null && p.getMinStockLevel() != null) ? p.getMinStockLevel() : BigDecimal.ZERO;
            BigDecimal netPos = (r.getCurrentStock() != null ? r.getCurrentStock() : BigDecimal.ZERO)
                    .add(r.getIncomingStock() != null ? r.getIncomingStock() : BigDecimal.ZERO);
            BigDecimal thresholdReq = minStock.subtract(netPos).max(BigDecimal.ZERO);
            BigDecimal forecastReq = (r.getTargetStock() != null) ? r.getTargetStock().subtract(netPos).max(BigDecimal.ZERO) : BigDecimal.ZERO;
            String reason = deriveRecommendationReason(forecastReq, thresholdReq);

            return RestockRecommendationDto.builder()
                    .id(r.getId())
                    .productId(r.getProductId())
                    .productName(p != null ? p.getName() : "Product #" + r.getProductId())
                    .sku(p != null ? p.getSku() : null)
                    .category(p != null ? p.getCategory() : null)
                    .unit(p != null ? p.getUnit() : "pcs")
                    .currentStock(r.getCurrentStock())
                    .incomingStock(r.getIncomingStock())
                    .minStockLevel(minStock)
                    .thresholdRequirement(thresholdReq)
                    .forecastRecommendedQty(forecastReq)
                    .recommendationReason(reason)
                    .forecast30d(r.getTotalHorizonDemand() != null ? r.getTotalHorizonDemand().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .recommendedOrderQty(r.getRecommendedOrderQty())
                    .expectedStockoutDate(r.getExpectedStockoutDate())
                    .urgencyStatus(r.getUrgencyStatus())
                    .confidenceLevel(r.getConfidenceLevel())
                    .unitCost(unitCost)
                    .estimatedTotalCost(estCost)
                    .explanationText(r.getExplanationText())
                    .generatedAt(r.getGeneratedAt())
                    .build();
        }).collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public RestockRecommendationDto getRecommendationForProduct(Long firmId, Long productId) {
        Product p = productRepo.findByIdAndFirmId(productId, firmId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        Optional<ProductRestockRecommendation> opt = restockRepo.findTopByFirmIdAndProductIdAndRecommendationStatusOrderByGeneratedAtDesc(
                firmId, productId, "ACTIVE");

        BigDecimal unitCost = (p.getCostPrice() != null) ? p.getCostPrice() : (p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO);
        BigDecimal minStock = (p.getMinStockLevel() != null) ? p.getMinStockLevel() : BigDecimal.ZERO;

        if (opt.isPresent()) {
            ProductRestockRecommendation r = opt.get();
            BigDecimal estCost = (unitCost != null && r.getRecommendedOrderQty() != null)
                    ? unitCost.multiply(r.getRecommendedOrderQty()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            BigDecimal netPos = (r.getCurrentStock() != null ? r.getCurrentStock() : BigDecimal.ZERO)
                    .add(r.getIncomingStock() != null ? r.getIncomingStock() : BigDecimal.ZERO);
            BigDecimal thresholdReq = minStock.subtract(netPos).max(BigDecimal.ZERO);
            BigDecimal forecastReq = (r.getTargetStock() != null) ? r.getTargetStock().subtract(netPos).max(BigDecimal.ZERO) : BigDecimal.ZERO;
            String reason = deriveRecommendationReason(forecastReq, thresholdReq);

            return RestockRecommendationDto.builder()
                    .id(r.getId())
                    .productId(r.getProductId())
                    .productName(p.getName())
                    .sku(p.getSku())
                    .category(p.getCategory())
                    .unit(p.getUnit() != null ? p.getUnit() : "pcs")
                    .currentStock(r.getCurrentStock())
                    .incomingStock(r.getIncomingStock())
                    .minStockLevel(minStock)
                    .thresholdRequirement(thresholdReq)
                    .forecastRecommendedQty(forecastReq)
                    .recommendationReason(reason)
                    .forecast30d(r.getTotalHorizonDemand() != null ? r.getTotalHorizonDemand().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                    .recommendedOrderQty(r.getRecommendedOrderQty())
                    .expectedStockoutDate(r.getExpectedStockoutDate())
                    .urgencyStatus(r.getUrgencyStatus())
                    .confidenceLevel(r.getConfidenceLevel())
                    .unitCost(unitCost)
                    .estimatedTotalCost(estCost)
                    .explanationText(r.getExplanationText())
                    .generatedAt(r.getGeneratedAt())
                    .build();
        }

        BigDecimal currentStock = p.getStockQuantity() != null ? p.getStockQuantity() : BigDecimal.ZERO;
        BigDecimal incomingStock = calculateIncomingIssuedPoStock(firmId, productId);
        BigDecimal netPos = currentStock.add(incomingStock);
        BigDecimal thresholdReq = minStock.subtract(netPos).max(BigDecimal.ZERO);
        String reason = thresholdReq.compareTo(BigDecimal.ZERO) > 0 ? "MINIMUM_STOCK" : "HEALTHY";

        return RestockRecommendationDto.builder()
                .productId(productId)
                .productName(p.getName())
                .sku(p.getSku())
                .category(p.getCategory())
                .unit(p.getUnit() != null ? p.getUnit() : "pcs")
                .currentStock(currentStock)
                .incomingStock(incomingStock)
                .minStockLevel(minStock)
                .thresholdRequirement(thresholdReq)
                .forecastRecommendedQty(BigDecimal.ZERO)
                .recommendationReason(reason)
                .forecast30d(BigDecimal.ZERO)
                .recommendedOrderQty(thresholdReq)
                .urgencyStatus(thresholdReq.compareTo(BigDecimal.ZERO) > 0 ? (currentStock.compareTo(BigDecimal.ZERO) <= 0 ? "OUT_OF_STOCK" : "REORDER_NEEDED") : "HEALTHY")
                .confidenceLevel("LOW")
                .unitCost(unitCost)
                .estimatedTotalCost(unitCost.multiply(thresholdReq).setScale(2, RoundingMode.HALF_UP))
                .explanationText(String.format("Minimum stock requirement: %s units (Configured min: %s, Current: %s, Incoming: %s).",
                        thresholdReq.stripTrailingZeros().toPlainString(), minStock.stripTrailingZeros().toPlainString(),
                        currentStock.stripTrailingZeros().toPlainString(), incomingStock.stripTrailingZeros().toPlainString()))
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
