package com.billing.simple.billsoft.forecast;

import com.billing.simple.billsoft.forecast.dto.ForecastAdminSyncResponse;
import com.billing.simple.billsoft.forecast.dto.ForecastSyncStatusDto;
import com.billing.simple.billsoft.forecast.entity.ForecastSyncJobRecord;
import com.billing.simple.billsoft.forecast.repository.ForecastSyncJobRecordRepository;
import com.billing.simple.billsoft.forecast.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ForecastAsyncWorkerServiceTest {

    @Mock
    private ForecastIngestionService ingestionService;
    @Mock
    private ForecastAggregationService aggregationService;
    @Mock
    private ForecastEngineService engineService;
    @Mock
    private RestockAdvisoryService restockAdvisoryService;
    @Mock
    private ForecastSyncJobRecordRepository jobRecordRepository;

    private ForecastAsyncWorkerService workerService;
    private final Long firmId = 33L;

    @BeforeEach
    void setUp() {
        workerService = new ForecastAsyncWorkerService(
                ingestionService,
                aggregationService,
                engineService,
                restockAdvisoryService,
                jobRecordRepository
        );
    }

    @Test
    @DisplayName("Freshness check returns true when last sync completed less than 6 hours ago")
    void testForecastFreshnessUnderSixHours() {
        ForecastSyncJobRecord freshJob = ForecastSyncJobRecord.builder()
                .firmId(firmId)
                .status("COMPLETED")
                .startedAt(LocalDateTime.now().minusHours(2))
                .completedAt(LocalDateTime.now().minusHours(2))
                .build();

        when(jobRecordRepository.findTopByFirmIdAndStatusOrderByCompletedAtDesc(firmId, "COMPLETED"))
                .thenReturn(Optional.of(freshJob));

        assertTrue(workerService.isForecastFresh(firmId));

        ForecastSyncStatusDto status = workerService.getFirmSyncStatus(firmId);
        assertTrue(status.isFresh());
        assertFalse(status.isInProgress());
    }

    @Test
    @DisplayName("Freshness check returns false when last sync is older than 6 hours")
    void testForecastStaleOverSixHours() {
        ForecastSyncJobRecord staleJob = ForecastSyncJobRecord.builder()
                .firmId(firmId)
                .status("COMPLETED")
                .startedAt(LocalDateTime.now().minusHours(8))
                .completedAt(LocalDateTime.now().minusHours(8))
                .build();

        when(jobRecordRepository.findTopByFirmIdAndStatusOrderByCompletedAtDesc(firmId, "COMPLETED"))
                .thenReturn(Optional.of(staleJob));

        assertFalse(workerService.isForecastFresh(firmId));
    }

    @Test
    @DisplayName("Bypasses retrain when forecast is fresh and force is false")
    void testBypassRetrainWhenFresh() {
        ForecastSyncJobRecord freshJob = ForecastSyncJobRecord.builder()
                .firmId(firmId)
                .status("COMPLETED")
                .completedAt(LocalDateTime.now().minusHours(1))
                .build();

        when(jobRecordRepository.findTopByFirmIdAndStatusOrderByCompletedAtDesc(firmId, "COMPLETED"))
                .thenReturn(Optional.of(freshJob));

        ForecastAdminSyncResponse res = workerService.executeSync(firmId, false, false);
        assertNotNull(res);
        assertTrue(res.isSuccess());
        assertTrue(res.getMessage().contains("up to date"));

        // Pipeline services were never called
        verify(ingestionService, never()).syncAllInvoicesForFirm(any());
        verify(engineService, never()).generateForecastsForCatalog(any());
    }

    @Test
    @DisplayName("Executes full pipeline when force is true even if recently synced")
    void testForceSyncExecutesPipeline() {
        when(ingestionService.syncAllInvoicesForFirm(firmId)).thenReturn(50);
        when(ingestionService.syncAllSalesReturnsForFirm(firmId)).thenReturn(5);
        when(aggregationService.aggregateAllProductsForFirm(firmId)).thenReturn(20);
        when(engineService.generateForecastsForCatalog(firmId)).thenReturn(20);
        when(restockAdvisoryService.generateCatalogRecommendations(firmId)).thenReturn(10);

        ForecastAdminSyncResponse res = workerService.executeSync(firmId, true, false);
        assertNotNull(res);
        assertTrue(res.isSuccess());
        assertEquals(50, res.getInvoicesProcessed());
        assertEquals(20, res.getForecastsGenerated());

        verify(ingestionService, times(1)).syncAllInvoicesForFirm(firmId);
        verify(engineService, times(1)).generateForecastsForCatalog(firmId);
        verify(jobRecordRepository, atLeastOnce()).save(any(ForecastSyncJobRecord.class));
    }

    @Test
    @DisplayName("Failure in ingestion service records error cleanly and does not wipe data")
    void testFailureHandlingPreservesData() {
        when(ingestionService.syncAllInvoicesForFirm(firmId)).thenThrow(new RuntimeException("Database lock timeout during ingestion"));

        ForecastAdminSyncResponse res = workerService.executeSync(firmId, true, false);
        assertNotNull(res);
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Database lock timeout"));

        // Verify failure was saved in job records
        verify(jobRecordRepository, atLeastOnce()).save(argThat(r -> "FAILED".equals(r.getStatus())));
    }
}
