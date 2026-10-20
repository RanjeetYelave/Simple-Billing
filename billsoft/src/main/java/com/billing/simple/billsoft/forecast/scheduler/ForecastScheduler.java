package com.billing.simple.billsoft.forecast.scheduler;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.forecast.service.ForecastAggregationService;
import com.billing.simple.billsoft.forecast.service.ForecastEngineService;
import com.billing.simple.billsoft.forecast.service.ForecastIngestionService;
import com.billing.simple.billsoft.forecast.service.RestockAdvisoryService;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;
import com.billing.simple.billsoft.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tenant-safe background scheduler for incremental data sync and nightly forecast/restock runs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForecastScheduler {

    private final FirmDetailsRepository firmDetailsRepository;
    private final ForecastIngestionService ingestionService;
    private final ForecastAggregationService aggregationService;
    private final ForecastEngineService engineService;
    private final RestockAdvisoryService restockAdvisoryService;

    /**
     * Incremental sync every 15 minutes by default.
     */
    @Scheduled(cron = "${billsoft.forecast.schedule.incremental-cron:0 */15 * * * *}")
    public void runIncrementalSync() {
        log.info("Starting scheduled incremental forecast synchronization across all firms");
        List<FirmDetails> firms = firmDetailsRepository.findAll();
        for (FirmDetails firm : firms) {
            Long firmId = firm.getId();
            if (firmId == null) continue;
            try {
                TenantContext.setCurrentFirmId(firmId);
                ingestionService.syncAllInvoicesForFirm(firmId);
                ingestionService.syncAllSalesReturnsForFirm(firmId);
                aggregationService.aggregateAllProductsForFirm(firmId);
            } catch (Exception e) {
                log.error("Error during scheduled incremental sync for firm {}", firmId, e);
            } finally {
                TenantContext.clear();
            }
        }
        log.info("Completed scheduled incremental forecast synchronization");
    }

    /**
     * Comprehensive model training and replenishment generation nightly at 2:00 AM by default.
     */
    @Scheduled(cron = "${billsoft.forecast.schedule.nightly-cron:0 0 2 * * *}")
    public void runNightlyForecastAndRestock() {
        log.info("Starting scheduled nightly forecast training & replenishment generation across all firms");
        List<FirmDetails> firms = firmDetailsRepository.findAll();
        for (FirmDetails firm : firms) {
            Long firmId = firm.getId();
            if (firmId == null) continue;
            try {
                TenantContext.setCurrentFirmId(firmId);
                ingestionService.syncAllInvoicesForFirm(firmId);
                ingestionService.syncAllSalesReturnsForFirm(firmId);
                aggregationService.aggregateAllProductsForFirm(firmId);
                engineService.generateForecastsForCatalog(firmId);
                restockAdvisoryService.generateCatalogRecommendations(firmId);
            } catch (Exception e) {
                log.error("Error during scheduled nightly forecast run for firm {}", firmId, e);
            } finally {
                TenantContext.clear();
            }
        }
        log.info("Completed scheduled nightly forecast training & replenishment generation");
    }
}
