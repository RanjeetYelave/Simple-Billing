package com.billing.simple.billsoft.forecast.service;

import com.billing.simple.billsoft.forecast.dto.ForecastAdminSyncResponse;
import com.billing.simple.billsoft.forecast.dto.ForecastSyncStatusDto;
import com.billing.simple.billsoft.forecast.entity.ForecastSyncJobRecord;
import com.billing.simple.billsoft.forecast.repository.ForecastSyncJobRecordRepository;
import com.billing.simple.billsoft.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Headless asynchronous worker managing forecast synchronization lifecycle.
 * Provides firm-level isolation, bounded background worker pool, durable job state,
 * in-flight deduplication, and a defensible freshness policy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastAsyncWorkerService {

    private final ForecastIngestionService ingestionService;
    private final ForecastAggregationService aggregationService;
    private final ForecastEngineService engineService;
    private final RestockAdvisoryService restockAdvisoryService;
    private final ForecastSyncJobRecordRepository jobRecordRepository;

    private final Map<Long, ReentrantLock> firmSyncLocks = new ConcurrentHashMap<>();
    private final ExecutorService backgroundPool = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "rupeecrm-forecast-worker-" + (++counter));
                    t.setDaemon(true);
                    return t;
                }
            }
    );

    /**
     * Freshness threshold: 6 hours fallback.
     */
    private static final long FRESHNESS_THRESHOLD_HOURS = 6;

    /**
     * Determines whether active firm's forecast is fresh enough to bypass full recalculation.
     */
    public boolean isForecastFresh(Long firmId) {
        if (firmId == null) return false;
        Optional<ForecastSyncJobRecord> lastJobOpt = jobRecordRepository.findTopByFirmIdAndStatusOrderByCompletedAtDesc(firmId, "COMPLETED");
        if (lastJobOpt.isEmpty()) {
            return false;
        }
        ForecastSyncJobRecord lastJob = lastJobOpt.get();
        if (lastJob.getCompletedAt() == null) {
            return false;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusHours(FRESHNESS_THRESHOLD_HOURS);
        return lastJob.getCompletedAt().isAfter(cutoff);
    }

    /**
     * Current synchronisation status for the specified firm.
     */
    public ForecastSyncStatusDto getFirmSyncStatus(Long firmId) {
        ReentrantLock lock = firmSyncLocks.computeIfAbsent(firmId, k -> new ReentrantLock());
        boolean inProgress = lock.isLocked();

        Optional<ForecastSyncJobRecord> lastJobOpt = jobRecordRepository.findTopByFirmIdOrderByStartedAtDesc(firmId);
        LocalDateTime completedAt = null;
        String status = "NEVER_RUN";
        String message = "No prior synchronization recorded.";
        long duration = 0L;

        if (lastJobOpt.isPresent()) {
            ForecastSyncJobRecord j = lastJobOpt.get();
            completedAt = j.getCompletedAt();
            status = j.getStatus();
            message = j.getMessage();
            duration = j.getDurationMs();
        }

        return ForecastSyncStatusDto.builder()
                .firmId(firmId)
                .inProgress(inProgress)
                .isFresh(isForecastFresh(firmId))
                .lastSyncCompletedAt(completedAt)
                .lastSyncStatus(status)
                .message(message)
                .durationMs(duration)
                .build();
    }

    /**
     * Core pipeline execution. Supports both synchronous execution (for test assertions)
     * and non-blocking asynchronous execution (for application startup and UI responsive calls).
     */
    public ForecastAdminSyncResponse executeSync(Long firmId, boolean force, boolean isAsync) {
        if (firmId == null) {
            throw new IllegalArgumentException("Firm ID is required for forecast synchronization");
        }

        // Freshness check: if not forced and data is fresh, skip heavy pipeline
        if (!force && isForecastFresh(firmId)) {
            log.info("Forecast for firm {} is fresh (< {} hours old). Bypassing retraining.", firmId, FRESHNESS_THRESHOLD_HOURS);
            return ForecastAdminSyncResponse.builder()
                    .success(true)
                    .firmId(firmId)
                    .invoicesProcessed(0)
                    .returnsProcessed(0)
                    .productsAggregated(0)
                    .forecastsGenerated(0)
                    .recommendationsGenerated(0)
                    .executionDurationMs(0L)
                    .message("Forecasts are up to date (< " + FRESHNESS_THRESHOLD_HOURS + " hours old). Full retrain bypassed.")
                    .build();
        }

        ReentrantLock lock = firmSyncLocks.computeIfAbsent(firmId, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            log.info("Forecast synchronization already in progress for firm {}. Returning concurrent-safe status.", firmId);
            return ForecastAdminSyncResponse.builder()
                    .success(true)
                    .firmId(firmId)
                    .invoicesProcessed(0)
                    .returnsProcessed(0)
                    .productsAggregated(0)
                    .forecastsGenerated(0)
                    .recommendationsGenerated(0)
                    .executionDurationMs(0L)
                    .message("Forecast synchronization is currently in progress for firm " + firmId + ".")
                    .build();
        }

        // Record IN_PROGRESS job
        ForecastSyncJobRecord jobRecord = ForecastSyncJobRecord.builder()
                .firmId(firmId)
                .status("IN_PROGRESS")
                .startedAt(LocalDateTime.now())
                .message("Pipeline executing")
                .build();
        jobRecordRepository.save(jobRecord);

        if (isAsync) {
            backgroundPool.submit(() -> {
                try {
                    TenantContext.setCurrentFirmId(firmId);
                    runPipelineCore(firmId, lock, jobRecord);
                } finally {
                    TenantContext.clear();
                }
            });
            return ForecastAdminSyncResponse.builder()
                    .success(true)
                    .firmId(firmId)
                    .invoicesProcessed(0)
                    .returnsProcessed(0)
                    .productsAggregated(0)
                    .forecastsGenerated(0)
                    .recommendationsGenerated(0)
                    .executionDurationMs(0L)
                    .message("Forecast synchronization dispatched in background.")
                    .build();
        } else {
            return runPipelineCore(firmId, lock, jobRecord);
        }
    }

    private ForecastAdminSyncResponse runPipelineCore(Long firmId, ReentrantLock lock, ForecastSyncJobRecord jobRecord) {
        long start = System.currentTimeMillis();
        try {
            int inv = ingestionService.syncAllInvoicesForFirm(firmId);
            int ret = ingestionService.syncAllSalesReturnsForFirm(firmId);
            int agg = aggregationService.aggregateAllProductsForFirm(firmId);
            int fc = engineService.generateForecastsForCatalog(firmId);
            int rec = restockAdvisoryService.generateCatalogRecommendations(firmId);

            long duration = System.currentTimeMillis() - start;

            jobRecord.setStatus("COMPLETED");
            jobRecord.setInvoicesProcessed(inv);
            jobRecord.setReturnsProcessed(ret);
            jobRecord.setProductsAggregated(agg);
            jobRecord.setForecastsGenerated(fc);
            jobRecord.setRecommendationsGenerated(rec);
            jobRecord.setDurationMs(duration);
            jobRecord.setCompletedAt(LocalDateTime.now());
            jobRecord.setMessage("Pipeline executed successfully.");
            jobRecord.setErrorMessage(null);
            jobRecordRepository.save(jobRecord);

            log.info("Forecast sync completed for firm {} in {}ms (Invoices: {}, Returns: {}, Agg: {}, Models: {}, Recs: {})",
                    firmId, duration, inv, ret, agg, fc, rec);

            return ForecastAdminSyncResponse.builder()
                    .success(true)
                    .firmId(firmId)
                    .invoicesProcessed(inv)
                    .returnsProcessed(ret)
                    .productsAggregated(agg)
                    .forecastsGenerated(fc)
                    .recommendationsGenerated(rec)
                    .executionDurationMs(duration)
                    .message("Forecasting pipeline executed successfully.")
                    .build();
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Failed to execute forecast sync pipeline for firm {}", firmId, e);

            jobRecord.setStatus("FAILED");
            jobRecord.setDurationMs(duration);
            jobRecord.setCompletedAt(LocalDateTime.now());
            jobRecord.setMessage("Pipeline execution failed");
            jobRecord.setErrorMessage(e.getMessage());
            jobRecordRepository.save(jobRecord);

            return ForecastAdminSyncResponse.builder()
                    .success(false)
                    .firmId(firmId)
                    .invoicesProcessed(0)
                    .returnsProcessed(0)
                    .productsAggregated(0)
                    .forecastsGenerated(0)
                    .recommendationsGenerated(0)
                    .executionDurationMs(duration)
                    .message("Forecast sync failed: " + e.getMessage())
                    .build();
        } finally {
            lock.unlock();
        }
    }
}
