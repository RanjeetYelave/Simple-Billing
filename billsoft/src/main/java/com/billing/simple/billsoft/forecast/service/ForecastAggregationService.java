package com.billing.simple.billsoft.forecast.service;

import com.billing.simple.billsoft.forecast.entity.ForecastRealizationLog;
import com.billing.simple.billsoft.forecast.entity.ProductSalesMetrics;
import com.billing.simple.billsoft.forecast.repository.ForecastRealizationLogRepository;
import com.billing.simple.billsoft.forecast.repository.ForecastSalesTransactionRepository;
import com.billing.simple.billsoft.forecast.repository.ProductSalesMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Aggregates discrete transaction events into daily product metrics for fast UI charts and time-series models.
 * Evaluates pending forecast realization predictions against actual incoming sales facts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastAggregationService {

    private final ForecastSalesTransactionRepository transactionRepo;
    private final ProductSalesMetricsRepository metricsRepo;
    private final ForecastRealizationLogRepository realizationLogRepo;

    @Transactional
    public void aggregateProductDailyMetrics(Long firmId, Long productId, LocalDate metricDate) {
        if (firmId == null || productId == null || metricDate == null) return;

        List<Object[]> rows = transactionRepo.aggregateMetricsForProductAndDate(firmId, productId, metricDate);
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            // No transactions on this date -> if a metric row existed, delete it
            metricsRepo.findByFirmIdAndProductIdAndMetricDate(firmId, productId, metricDate)
                    .ifPresent(metricsRepo::delete);
            return;
        }

        Object[] r = rows.get(0);
        BigDecimal grossReq = r[0] != null ? (BigDecimal) r[0] : BigDecimal.ZERO;
        BigDecimal grossFul = r[1] != null ? (BigDecimal) r[1] : BigDecimal.ZERO;
        BigDecimal shortage = r[2] != null ? (BigDecimal) r[2] : BigDecimal.ZERO;
        BigDecimal returned = r[3] != null ? (BigDecimal) r[3] : BigDecimal.ZERO;
        BigDecimal grossSales = r[4] != null ? (BigDecimal) r[4] : BigDecimal.ZERO;
        BigDecimal refundAmount = r[5] != null ? (BigDecimal) r[5] : BigDecimal.ZERO;
        BigDecimal minPrice = (BigDecimal) r[6];
        BigDecimal maxPrice = (BigDecimal) r[7];
        int uniqueCusts = r[8] != null ? ((Number) r[8]).intValue() : 0;

        if (grossReq.compareTo(BigDecimal.ZERO) == 0 && returned.compareTo(BigDecimal.ZERO) == 0 && grossSales.compareTo(BigDecimal.ZERO) == 0) {
            metricsRepo.findByFirmIdAndProductIdAndMetricDate(firmId, productId, metricDate)
                    .ifPresent(metricsRepo::delete);
            return;
        }

        BigDecimal netReq = grossReq.subtract(returned);
        BigDecimal netSales = grossSales.subtract(refundAmount);

        BigDecimal realizedPrice = null;
        if (grossReq.compareTo(BigDecimal.ZERO) > 0) {
            realizedPrice = grossSales.divide(grossReq, 2, RoundingMode.HALF_UP);
        }

        BigDecimal netRealizedPrice = null;
        if (netReq.compareTo(BigDecimal.ZERO) > 0) {
            netRealizedPrice = netSales.divide(netReq, 2, RoundingMode.HALF_UP);
        }

        Optional<ProductSalesMetrics> opt = metricsRepo.findByFirmIdAndProductIdAndMetricDate(firmId, productId, metricDate);
        ProductSalesMetrics m = opt.orElseGet(() -> ProductSalesMetrics.builder()
                .firmId(firmId)
                .productId(productId)
                .metricDate(metricDate)
                .build());

        m.setGrossRequestedQty(grossReq);
        m.setGrossFulfilledQty(grossFul);
        m.setShortageQty(shortage);
        m.setReturnedQty(returned);
        m.setNetRequestedQty(netReq);
        m.setGrossSales(grossSales);
        m.setRefundAmount(refundAmount);
        m.setNetSales(netSales);
        m.setTransactionTimeRealizedPrice(realizedPrice);
        m.setNetRealizedPrice(netRealizedPrice);
        m.setMinRealizedPrice(minPrice);
        m.setMaxRealizedPrice(maxPrice);
        m.setUniqueCustomerCount(uniqueCusts);

        metricsRepo.save(m);
        evaluatePendingRealizations(firmId, productId, metricDate, netReq);
    }

    private void evaluatePendingRealizations(Long firmId, Long productId, LocalDate metricDate, BigDecimal actualDemand) {
        if (realizationLogRepo == null || firmId == null || productId == null || metricDate == null) return;
        List<ForecastRealizationLog> pending = realizationLogRepo.findByFirmIdAndProductIdAndTargetDate(firmId, productId, metricDate);
        for (ForecastRealizationLog logEntry : pending) {
            if ("PENDING".equalsIgnoreCase(logEntry.getEvaluationStatus())) {
                logEntry.setActualDemand(actualDemand != null ? actualDemand : BigDecimal.ZERO);
                BigDecimal pred = logEntry.getPredictedDemand() != null ? logEntry.getPredictedDemand() : BigDecimal.ZERO;
                logEntry.setAbsoluteError(pred.subtract(logEntry.getActualDemand()).abs());
                logEntry.setEvaluationStatus("EVALUATED");
                logEntry.setEvaluatedAt(LocalDateTime.now());
                realizationLogRepo.save(logEntry);
            }
        }
    }

    @Transactional
    public int aggregateAffectedDates(Long firmId, LocalDateTime since) {
        List<LocalDate> modifiedDates = transactionRepo.findDistinctTransactionDatesModifiedSince(firmId, since);
        List<Long> productIds = transactionRepo.findDistinctProductIdsWithTransactions(firmId);
        int count = 0;
        for (Long pId : productIds) {
            for (LocalDate date : modifiedDates) {
                aggregateProductDailyMetrics(firmId, pId, date);
                count++;
            }
        }
        return count;
    }

    @Transactional
    public int aggregateAllProducts(Long firmId) {
        if (firmId == null) return 0;

        List<Object[]> batchRows = transactionRepo.batchAggregateMetricsForFirm(firmId);
        if (batchRows == null || batchRows.isEmpty()) {
            return 0;
        }

        List<ProductSalesMetrics> toSave = new ArrayList<>(batchRows.size());
        for (Object[] r : batchRows) {
            Long productId = ((Number) r[0]).longValue();
            LocalDate metricDate = (LocalDate) r[1];
            BigDecimal grossReq = r[2] != null ? (BigDecimal) r[2] : BigDecimal.ZERO;
            BigDecimal grossFul = r[3] != null ? (BigDecimal) r[3] : BigDecimal.ZERO;
            BigDecimal shortage = r[4] != null ? (BigDecimal) r[4] : BigDecimal.ZERO;
            BigDecimal returned = r[5] != null ? (BigDecimal) r[5] : BigDecimal.ZERO;
            BigDecimal grossSales = r[6] != null ? (BigDecimal) r[6] : BigDecimal.ZERO;
            BigDecimal refundAmount = r[7] != null ? (BigDecimal) r[7] : BigDecimal.ZERO;
            BigDecimal minPrice = (BigDecimal) r[8];
            BigDecimal maxPrice = (BigDecimal) r[9];
            int uniqueCusts = r[10] != null ? ((Number) r[10]).intValue() : 0;

            if (grossReq.compareTo(BigDecimal.ZERO) == 0 && returned.compareTo(BigDecimal.ZERO) == 0 && grossSales.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            BigDecimal netReq = grossReq.subtract(returned);
            BigDecimal netSales = grossSales.subtract(refundAmount);

            BigDecimal realizedPrice = null;
            if (grossReq.compareTo(BigDecimal.ZERO) > 0) {
                realizedPrice = grossSales.divide(grossReq, 2, RoundingMode.HALF_UP);
            }

            BigDecimal netRealizedPrice = null;
            if (netReq.compareTo(BigDecimal.ZERO) > 0) {
                netRealizedPrice = netSales.divide(netReq, 2, RoundingMode.HALF_UP);
            }

            Optional<ProductSalesMetrics> opt = metricsRepo.findByFirmIdAndProductIdAndMetricDate(firmId, productId, metricDate);
            ProductSalesMetrics m = opt.orElseGet(() -> ProductSalesMetrics.builder()
                    .firmId(firmId)
                    .productId(productId)
                    .metricDate(metricDate)
                    .build());

            m.setGrossRequestedQty(grossReq);
            m.setGrossFulfilledQty(grossFul);
            m.setShortageQty(shortage);
            m.setReturnedQty(returned);
            m.setNetRequestedQty(netReq);
            m.setGrossSales(grossSales);
            m.setRefundAmount(refundAmount);
            m.setNetSales(netSales);
            m.setTransactionTimeRealizedPrice(realizedPrice);
            m.setNetRealizedPrice(netRealizedPrice);
            m.setMinRealizedPrice(minPrice);
            m.setMaxRealizedPrice(maxPrice);
            m.setUniqueCustomerCount(uniqueCusts);

            toSave.add(m);
            evaluatePendingRealizations(firmId, productId, metricDate, netReq);
        }

        metricsRepo.saveAll(toSave);
        log.info("Batch aggregated {} daily metrics records across firm {}", toSave.size(), firmId);
        return toSave.size();
    }

    @Transactional
    public int aggregateAllProductsForFirm(Long firmId) {
        return aggregateAllProducts(firmId);
    }
}
