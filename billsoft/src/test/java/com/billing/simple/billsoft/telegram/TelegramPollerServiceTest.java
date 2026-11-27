package com.billing.simple.billsoft.telegram;

import com.billing.simple.billsoft.telegram.client.TelegramApiClient;
import com.billing.simple.billsoft.telegram.entities.TelegramConfig;
import com.billing.simple.billsoft.telegram.entities.TelegramUpdateInbox;
import com.billing.simple.billsoft.telegram.repo.TelegramConfigRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramUpdateInboxRepository;
import com.billing.simple.billsoft.telegram.service.TelegramDeliveryService;
import com.billing.simple.billsoft.telegram.service.TelegramPollerService;
import com.billing.simple.billsoft.telegram.service.TelegramUpdateDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TelegramPollerServiceTest {

    @Mock
    private TelegramConfigRepository configRepo;

    @Mock
    private TelegramUpdateInboxRepository inboxRepo;

    @Mock
    private TelegramApiClient apiClient;

    @Mock
    private TelegramUpdateDispatcher dispatcher;

    @Mock
    private TelegramDeliveryService deliveryService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ObjectMapper objectMapper = new ObjectMapper();
    private TelegramPollerService pollerService;
    private TelegramConfig config;

    @BeforeEach
    void setUp() {
        config = new TelegramConfig();
        config.setId(1L);
        config.setInstallationId("TEST-INST");
        config.setIsEnabled(true);
        config.setLastPollingOffset(100L);
        config.setPollingTimeoutSeconds(30);
        config.setCircuitBreakerTripped(false);
        config.setConflictCount(0);

        lenient().when(configRepo.findById(1L)).thenReturn(Optional.of(config));
        lenient().when(configRepo.save(any(TelegramConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        pollerService = new TelegramPollerService(
                configRepo, inboxRepo, apiClient, dispatcher, deliveryService, transactionManager, objectMapper
        );
    }

    @Test
    void testConflict409TripsCircuitBreakerAndHalts() throws Exception {
        when(apiClient.getUpdates(anyString(), any(), anyInt()))
                .thenThrow(new TelegramApiClient.TelegramConflictException("409 Conflict: terminated by other getUpdates request"));

        pollerService.setBotTokenAndStart("TEST-TOKEN");

        // Wait brief period for poller loop to encounter conflict
        Thread.sleep(300);

        assertFalse(pollerService.isRunning(), "Poller must halt immediately upon 409 conflict");
        assertTrue(config.getCircuitBreakerTripped(), "Circuit breaker must be tripped on 409 conflict");
        assertNotNull(config.getLastError());
        assertTrue(config.getLastError().contains("409 Conflict") || config.getLastError().contains("Halted"));
    }

    @Test
    void testResumePollerResetsCircuitBreaker() {
        config.setCircuitBreakerTripped(true);
        config.setLastError("Previous 409");

        pollerService.resumeCircuitBreaker();

        assertFalse(config.getCircuitBreakerTripped(), "Resuming poller must reset circuit breaker flag");
        assertNull(config.getLastError(), "Error must be cleared");
        assertEquals(0, config.getConflictCount(), "Conflict count must reset to 0");
    }

    @Test
    void testEmptyPollingResponseContinuesLoop() throws Exception {
        com.fasterxml.jackson.databind.JsonNode emptyResponse = objectMapper.readTree("{\"ok\":true,\"result\":[]}");
        when(apiClient.getUpdates(anyString(), any(), anyInt())).thenReturn(emptyResponse);

        pollerService.setBotTokenAndStart("TEST-TOKEN");
        Thread.sleep(150);

        assertTrue(pollerService.isRunning(), "Poller must continue running when updates array is empty");
        assertFalse(config.getCircuitBreakerTripped(), "Circuit breaker must not trip on empty polling response");
        assertNotNull(config.getLastActiveAt(), "lastActiveAt must be updated on successful empty poll");
        assertNotNull(config.getLastPollAt(), "lastPollAt must be updated");

        pollerService.stopPoller();
    }
}
