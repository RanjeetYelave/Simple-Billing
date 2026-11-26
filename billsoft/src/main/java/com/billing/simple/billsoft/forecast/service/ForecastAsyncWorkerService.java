package com.billing.simple.billsoft.forecast.service;

import com.billing.simple.billsoft.forecast.dto.ForecastAdminSyncResponse;
import com.billing.simple.billsoft.forecast.dto.ForecastSyncStatusDto;
import com.billing.simple.billsoft.forecast.entity.ForecastSyncJobRecord;
import com.billing.simple.billsoft.forecast.repository.ForecastSyncJobRecordRepository;
import com.billing.simple.billsoft.repo.InvoiceRepository;
import com.billing.simple.billsoft.repo.StockMovementRepository;
import com.billing.simple.billsoft.security.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * Headless asynchronous worker managing forecast synchronization lifecycle.
 * Provides firm-level isolation, bounded background worker pool, durable job state,
 * in-flight deduplication, heartbeat tracking, stale job recovery, and a defensible freshness policy.
 */
@Slf4j
@Service
public class ForecastAsyncWorkerService {

    private final ForecastIngestionService ingestionService;
    private final ForecastAggregationService aggregationService;
    private final ForecastEngineService engineService;
    private final RestockAdvisoryService restockAdvisoryService;
    private final ForecastSyncJobRecordRepository jobRecordRepository;
    private final InvoiceRepository invoiceRepo;
    private final StockMovementRepository stockMovementRepo;

    // Per-firm active execution locks safe across thread boundaries
    private final ConcurrentHashMap<Long, Boolean> activeFirmSyncs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> activeJobIdsByFirm = new ConcurrentHashMap<>();

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

    public ForecastAsyncWorkerService(
            ForecastIngestionService ingestionService,
            ForecastAggregationService aggregationService,
            ForecastEngineService engineService,
            RestockAdvisoryService restockAdvisoryService,
            ForecastSyncJobRecordRepository jobRecordRepository) {
        this(ingestionService, aggregationService, engineService, restockAdvisoryService, jobRecordRepository, null, null);
    }

    @Autowired
    public ForecastAsyncWorkerService(
            ForecastIngestionService ingestionService,
            ForecastAggregationService aggregationService,
            ForecastEngineService engineService,
            RestockAdvisoryService restockAdvisoryService,
            ForecastSyncJobRecordRepository jobRecordRepository,
            @Autowired(required = false) InvoiceRepository invoiceRepo,
            @Autowired(required = false) StockMovementRepository stockMovementRepo) {
        this.ingestionService = ingestionService;
        this.aggregationService = aggregationService;
        this.engineService = engineService;
        this.restockAdvisoryService = restockAdvisoryService;
        this.jobRecordRepository = jobRecordRepository;
        this.invoiceRepo = invoiceRepo;
        this.stockMovementRepo = stockMovementRepo;
    }

    /**
     * Determines whether active firm's forecast is fresh enough to bypass full recalculation.
     * Enforces source-aware invalidations: if new invoices or stock movements occurred
     * since the last completed synchronization, the forecast is considered stale.
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
        if (lastJob.getCompletedAt().isBefore(cutoff)) {
            return false;
        }

        // Source-aware invalidation checks:
        if (invoiceRepo != null) {
            try {
                long modifiedInvoices = invoiceRepo.countModifiedSince(firmId, lastJob.getCompletedAt());
                if (modifiedInvoices > 0) {
                    log.info("Forecast for firm {} invalidated: {} invoices created/modified since last sync", firmId, modifiedInvoices);
                    return false;
                }
            } catch (Exception e) {
                log.debug("Could not verify invoice modifications for firm {}: {}", firmId, e.getMessage());
            }
        }
        if (stockMovementRepo != null) {
            try {
                long newMovements = stockMovementRepo.countCreatedSince(firmId, lastJob.getCompletedAt());
                if (newMovements > 0) {
                    log.info("Forecast for firm {} invalidated: {} stock movements created since last sync", firmId, newMovements);
                    return false;
                }
            } catch (Exception e) {
                log.debug("Could not verify stock movements for firm {}: {}", firmId, e.getMessage());
            }
        }

        return true;
    }

    /**
     * Current synchronisation status for the specified firm.
     * Detects and recovers hung or stalled jobs automatically.
     */
    public ForecastSyncStatusDto getFirmSyncStatus(Long firmId) {
        if (firmId == null) {
            return ForecastSyncStatusDto.builder().inProgress(false).isFresh(false).build();
        }

        boolean activelyRunning = activeFirmSyncs.containsKey(firmId);
        Optional<ForecastSyncJobRecord> lastJobOpt = Optional.empty();
        try {
            lastJobOpt = jobRecordRepository.findTopByFirmIdOrderByStartedAtDesc(firmId);
        } catch (Exception e) {
            log.warn("Could not query last forecast sync job for firm {}: {}", firmId, e.getMessage());
        }

        if (lastJobOpt.isPresent()) {
            ForecastSyncJobRecord j = lastJobOpt.get();
            String status = j.getStatus();

            // Stale job recovery: if recorded as PENDING/RUNNING/IN_PROGRESS but no active thread is running it,
            // or if heartbeat has timed out (> 90 seconds without heartbeat)
            boolean isPendingOrRunning = "RUNNING".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status) || "IN_PROGRESS".equalsIgnoreCase(status);
            boolean heartbeatTimedOut = false;
            long secondsSinceHeartbeat = 0L;

            if (j.getHeartbeatAt() != null) {
                secondsSinceHeartbeat = Duration.between(j.getHeartbeatAt(), LocalDateTime.now()).getSeconds();
                if (secondsSinceHeartbeat > 90) {
                    heartbeatTimedOut = true;
                }
            } else if (j.getStartedAt() != null) {
                secondsSinceHeartbeat = Duration.between(j.getStartedAt(), LocalDateTime.now()).getSeconds();
                if (secondsSinceHeartbeat > 90) {
                    heartbeatTimedOut = true;
                }
            }

            if (isPendingOrRunning && (!activelyRunning || heartbeatTimedOut)) {
                log.warn("Recovering stale forecast sync job #{} for firm {}. Active thread: {}, Heartbeat age: {}s",
                        j.getId(), firmId, activelyRunning, secondsSinceHeartbeat);
                j.setStatus("FAILED");
                j.setStage("STALLED");
                j.setStageText("Forecast sync stalled");
                j.setMessage("Job timed out or worker interrupted");
                j.setErrorMessage("Synchronization stalled: worker heartbeat timed out");
                j.setCompletedAt(LocalDateTime.now());
                jobRecordRepository.save(j);
                activeFirmSyncs.remove(firmId);
                activeJobIdsByFirm.remove(firmId);
                activelyRunning = false;
                status = "FAILED";
            }

            boolean isStalled = "STALLED".equalsIgnoreCase(j.getStage()) || heartbeatTimedOut;
            String stageText = j.getStageText();
            if (stageText == null) {
                if ("COMPLETED".equalsIgnoreCase(status)) stageText = "Forecasts updated ✓";
                else if ("FAILED".equalsIgnoreCase(status)) stageText = isStalled ? "Forecast sync stalled" : "Forecast sync failed";
                else if ("SKIPPED_FRESH".equalsIgnoreCase(status)) stageText = "Forecasts up to date";
                else stageText = "Calculating forecasts...";
            }

            return ForecastSyncStatusDto.builder()
                    .firmId(firmId)
                    .jobId(j.getId())
                    .inProgress(activelyRunning && isPendingOrRunning)
                    .isFresh(isForecastFresh(firmId))
                    .isStalled(isStalled)
                    .stage(j.getStage() != null ? j.getStage() : status)
                    .stageText(stageText)
                    .progressPercent(j.getProgressPercent())
                    .lastSyncCompletedAt(j.getCompletedAt())
                    .lastSyncStatus(status)
                    .message(j.getMessage())
                    .durationMs(j.getDurationMs())
                    .invoicesProcessed(j.getInvoicesProcessed())
                    .productsAggregated(j.getProductsAggregated())
                    .forecastsGenerated(j.getForecastsGenerated())
                    .recommendationsGenerated(j.getRecommendationsGenerated())
                    .heartbeatAt(j.getHeartbeatAt())
                    .secondsSinceHeartbeat(secondsSinceHeartbeat)
                    .build();
        }

        return ForecastSyncStatusDto.builder()
                .firmId(firmId)
                .inProgress(activelyRunning)
                .isFresh(isForecastFresh(firmId))
                .isStalled(false)
                .stage("NEVER_RUN")
                .stageText("Forecasts not yet initialized")
                .progressPercent(0)
                .lastSyncStatus("NEVER_RUN")
                .message("No prior synchronization recorded.")
                .durationMs(0L)
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

        // Concurrency check using thread-safe per-firm lock
        if (activeFirmSyncs.putIfAbsent(firmId, Boolean.TRUE) != null) {
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

        // Record initial PENDING / RUNNING job
        ForecastSyncJobRecord initialRecord = ForecastSyncJobRecord.builder()
                .firmId(firmId)
                .status("PENDING")
                .stage("INITIALIZING")
                .stageText("Checking freshness...")
                .progressPercent(5)
                .startedAt(LocalDateTime.now())
                .heartbeatAt(LocalDateTime.now())
                .message("Pipeline execution queued")
                .build();
        ForecastSyncJobRecord savedRecord = null;
        try {
            savedRecord = jobRecordRepository.save(initialRecord);
            if (savedRecord != null && savedRecord.getId() != null) {
                activeJobIdsByFirm.put(firmId, savedRecord.getId());
            }
        } catch (Exception e) {
            log.warn("Could not persist initial sync job record for firm {}: {}", firmId, e.getMessage());
        }
        if (savedRecord == null) {
            savedRecord = initialRecord;
        }

        final ForecastSyncJobRecord effectiveRecord = savedRecord;

        if (isAsync) {
            backgroundPool.submit(() -> {
                try {
                    TenantContext.setCurrentFirmId(firmId);
                    runPipelineCore(firmId, effectiveRecord);
                } finally {
                    TenantContext.clear();
                    activeFirmSyncs.remove(firmId);
                    activeJobIdsByFirm.remove(firmId);
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
            try {
                return runPipelineCore(firmId, effectiveRecord);
            } finally {
                activeFirmSyncs.remove(firmId);
                activeJobIdsByFirm.remove(firmId);
            }
        }
    }

    private void updateProgress(ForecastSyncJobRecord job, String stage, String stageText, int percent) {
        job.setStage(stage);
        job.setStageText(stageText);
        job.setProgressPercent(percent);
        job.setHeartbeatAt(LocalDateTime.now());
        try {
            jobRecordRepository.save(job);
        } catch (Exception e) {
            log.warn("Failed to persist progress update for job #{}: {}", job.getId(), e.getMessage());
        }
    }

    private ForecastAdminSyncResponse runPipelineCore(Long firmId, ForecastSyncJobRecord jobRecord) {
        long start = System.currentTimeMillis();
        try {
            jobRecord.setStatus("RUNNING");
            updateProgress(jobRecord, "READING_SALES", "Reading sales data...", 20);

            int inv = ingestionService.syncAllInvoicesForFirm(firmId);
            int ret = ingestionService.syncAllSalesReturnsForFirm(firmId);

            updateProgress(jobRecord, "AGGREGATING_METRICS", "Aggregating sales patterns...", 45);
            int agg = aggregationService.aggregateAllProductsForFirm(firmId);

            updateProgress(jobRecord, "CALCULATING_FORECASTS", "Calculating forecasts...", 70);
            int fc = engineService.generateForecastsForCatalog(firmId);

            updateProgress(jobRecord, "UPDATING_RECOMMENDATIONS", "Updating recommendations...", 90);
            int rec = restockAdvisoryService.generateCatalogRecommendations(firmId);

            long duration = System.currentTimeMillis() - start;

            if (jobRecord != null) {
                jobRecord.setStatus("COMPLETED");
                jobRecord.setStage("COMPLETED");
                jobRecord.setStageText("Forecasts updated ✓");
                jobRecord.setProgressPercent(100);
                jobRecord.setInvoicesProcessed(inv);
                jobRecord.setReturnsProcessed(ret);
                jobRecord.setProductsAggregated(agg);
                jobRecord.setForecastsGenerated(fc);
                jobRecord.setRecommendationsGenerated(rec);
                jobRecord.setDurationMs(duration);
                jobRecord.setCompletedAt(LocalDateTime.now());
                jobRecord.setHeartbeatAt(LocalDateTime.now());
                jobRecord.setMessage("Pipeline executed successfully.");
                jobRecord.setErrorMessage(null);
                try {
                    jobRecordRepository.save(jobRecord);
                } catch (Exception ex) {
                    log.warn("Failed to persist completed job record for firm {}: {}", firmId, ex.getMessage());
                }
            }

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

            if (jobRecord != null) {
                jobRecord.setStatus("FAILED");
                jobRecord.setStage("FAILED");
                jobRecord.setStageText("Forecast sync failed");
                jobRecord.setDurationMs(duration);
                jobRecord.setCompletedAt(LocalDateTime.now());
                jobRecord.setHeartbeatAt(LocalDateTime.now());
                jobRecord.setMessage("Pipeline execution failed");
                jobRecord.setErrorMessage(e.getMessage());
                try {
                    jobRecordRepository.save(jobRecord);
                } catch (Exception ex) {
                    log.warn("Failed to persist failed job record for firm {}: {}", firmId, ex.getMessage());
                }
            }

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
        }
    }
}
