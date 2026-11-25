package com.billing.simple.billsoft.forecast.controller;

import com.billing.simple.billsoft.forecast.dto.*;
import com.billing.simple.billsoft.forecast.service.*;
import com.billing.simple.billsoft.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for Product Intelligence, Demand Forecasting, and Smart Restocking.
 * Enforces firm-level tenant isolation across all endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/forecast")
@CrossOrigin
@RequiredArgsConstructor
public class ForecastController {

    private final RestockAdvisoryService restockAdvisoryService;
    private final ForecastProductConfigService configService;
    private final ForecastIngestionService ingestionService;
    private final ForecastAggregationService aggregationService;
    private final ForecastEngineService engineService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.billing.simple.billsoft.forecast.service.ForecastAsyncWorkerService asyncWorkerService;

    private final java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.locks.ReentrantLock> firmSyncLocks = new java.util.concurrent.ConcurrentHashMap<>();

    private Long resolveFirmId(Long firmIdHeader, Long firmIdParam) {
        if (firmIdHeader != null && firmIdHeader > 0) {
            return firmIdHeader;
        }
        if (firmIdParam != null && firmIdParam > 0) {
            return firmIdParam;
        }
        return TenantContext.getOptionalFirmId().orElse(null);
    }

    /**
     * Comprehensive Product Intelligence KPI and metrics payload.
     */
    @GetMapping("/products/{id}/insights")
    public ResponseEntity<?> getProductInsights(
            @PathVariable("id") Long productId,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = resolveFirmId(firmIdHeader, firmIdParam);
        if (firmId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Tenant firm ID is required."));
        }
        try {
            ProductInsightResponse response = restockAdvisoryService.getInsights(firmId, productId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Historical daily/weekly demand and realized transaction price trends.
     */
    @GetMapping("/products/{id}/trends")
    public ResponseEntity<?> getProductTrends(
            @PathVariable("id") Long productId,
            @RequestParam(value = "range", required = false, defaultValue = "90d") String range,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = resolveFirmId(firmIdHeader, firmIdParam);
        if (firmId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Tenant firm ID is required."));
        }
        try {
            HistoricalTrendsResponse response = restockAdvisoryService.getTrends(firmId, productId, range);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 365-day single continuous daily forecast trajectory with 30/90/180/365 horizon summaries.
     */
    @GetMapping("/products/{id}/trajectory")
    public ResponseEntity<?> getProductTrajectory(
            @PathVariable("id") Long productId,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = resolveFirmId(firmIdHeader, firmIdParam);
        if (firmId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Tenant firm ID is required."));
        }
        try {
            ForecastTrajectoryResponse response = restockAdvisoryService.getTrajectory(firmId, productId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Advisory restock recommendation for a single product.
     */
    @GetMapping("/products/{id}/restock")
    public ResponseEntity<?> getProductRestock(
            @PathVariable("id") Long productId,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = resolveFirmId(firmIdHeader, firmIdParam);
        if (firmId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Tenant firm ID is required."));
        }
        try {
            RestockRecommendationDto response = restockAdvisoryService.getRecommendationForProduct(firmId, productId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Fully explainable replenishment math breakdown and audit trail.
     */
    @GetMapping("/products/{id}/restock/explanation")
    public ResponseEntity<?> getProductRestockExplanation(
            @PathVariable("id") Long productId,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = resolveFirmId(firmIdHeader, firmIdParam);
        if (firmId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Tenant firm ID is required."));
        }
        try {
            RestockExplanationDto response = restockAdvisoryService.getExplanation(firmId, productId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Product-level replenishment parameters configuration (lead time, MOQ, pack size, safety stock).
     */
    @GetMapping("/products/{id}/config")
    public ResponseEntity<?> getProductConfig(
            @PathVariable("id") Long productId,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = resolveFirmId(firmIdHeader, firmIdParam);
        if (firmId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Tenant firm ID is required."));
        }
        try {
            ForecastConfigDto config = configService.getConfigDto(firmId, productId);
            return ResponseEntity.ok(config);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/products/{id}/config")
    public ResponseEntity<?> updateProductConfig(
            @PathVariable("id") Long productId,
            @RequestBody ForecastConfigDto requestDto,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = resolveFirmId(firmIdHeader, firmIdParam);
        if (firmId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Tenant firm ID is required."));
        }
        try {
            ForecastConfigDto updated = configService.updateConfig(firmId, productId, requestDto);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Smart Reorder catalog view: active replenishment recommendations with urgency filters & pagination.
     */
    @GetMapping("/restock/recommendations")
    public ResponseEntity<?> getRestockRecommendations(
            @RequestParam(value = "urgency", required = false) String urgency,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "25") Integer size,
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = resolveFirmId(firmIdHeader, firmIdParam);
        if (firmId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Tenant firm ID is required."));
        }
        Pageable pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<RestockRecommendationDto> result = restockAdvisoryService.getActiveRecommendations(firmId, urgency, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * Batch inventory intelligence summary (30d velocity, days of coverage, urgency badges).
     */
    @GetMapping("/inventory-summary")
    public ResponseEntity<?> getInventorySummary(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = resolveFirmId(firmIdHeader, firmIdParam);
        if (firmId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Tenant firm ID is required."));
        }
        InventoryIntelligenceSummaryDto summary = restockAdvisoryService.getInventoryIntelligenceSummary(firmId);
        return ResponseEntity.ok(summary);
    }

    /**
     * Trigger on-demand full sync & forecast computation for a firm.
     */
    @PostMapping("/admin/sync")
    public ResponseEntity<?> triggerFirmSync(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam,
            @RequestParam(value = "force", required = false, defaultValue = "false") Boolean force,
            @RequestParam(value = "async", required = false, defaultValue = "false") Boolean isAsync) {
        Long firmId = resolveFirmId(firmIdHeader, firmIdParam);
        if (firmId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Tenant firm ID is required."));
        }

        if (asyncWorkerService != null) {
            return ResponseEntity.ok(asyncWorkerService.executeSync(firmId, Boolean.TRUE.equals(force), Boolean.TRUE.equals(isAsync)));
        }

        java.util.concurrent.locks.ReentrantLock lock = firmSyncLocks.computeIfAbsent(firmId, k -> new java.util.concurrent.locks.ReentrantLock());
        if (!lock.tryLock()) {
            log.info("Forecast synchronization already in progress for firm {}. Returning concurrent-safe status.", firmId);
            ForecastAdminSyncResponse inProgressResponse = ForecastAdminSyncResponse.builder()
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
            return ResponseEntity.ok(inProgressResponse);
        }

        long start = System.currentTimeMillis();
        try {
            int inv = ingestionService.syncAllInvoicesForFirm(firmId);
            int ret = ingestionService.syncAllSalesReturnsForFirm(firmId);
            int agg = aggregationService.aggregateAllProductsForFirm(firmId);
            int fc = engineService.generateForecastsForCatalog(firmId);
            int rec = restockAdvisoryService.generateCatalogRecommendations(firmId);

            long duration = System.currentTimeMillis() - start;
            ForecastAdminSyncResponse response = ForecastAdminSyncResponse.builder()
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

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to execute forecast sync for firm {}", firmId, e);
            long duration = System.currentTimeMillis() - start;
            ForecastAdminSyncResponse response = ForecastAdminSyncResponse.builder()
                    .success(false)
                    .firmId(firmId)
                    .executionDurationMs(duration)
                    .message("Sync failed: " + e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } finally {
            lock.unlock();
        }
    }

    public ResponseEntity<?> triggerFirmSync(Long firmIdHeader, Long firmIdParam) {
        return triggerFirmSync(firmIdHeader, firmIdParam, false, false);
    }

    /**
     * Check background forecast synchronization and data freshness status.
     */
    @GetMapping("/sync/status")
    public ResponseEntity<?> getSyncStatus(
            @RequestHeader(value = "X-Firm-Id", required = false) Long firmIdHeader,
            @RequestParam(value = "firmId", required = false) Long firmIdParam) {
        Long firmId = resolveFirmId(firmIdHeader, firmIdParam);
        if (firmId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Tenant firm ID is required."));
        }
        if (asyncWorkerService != null) {
            return ResponseEntity.ok(asyncWorkerService.getFirmSyncStatus(firmId));
        }
        return ResponseEntity.ok(Map.of("firmId", firmId, "inProgress", false, "isFresh", true));
    }
}
