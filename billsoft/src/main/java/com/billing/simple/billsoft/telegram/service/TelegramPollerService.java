package com.billing.simple.billsoft.telegram.service;

import com.billing.simple.billsoft.telegram.client.TelegramApiClient;
import com.billing.simple.billsoft.telegram.entities.TelegramConfig;
import com.billing.simple.billsoft.telegram.entities.TelegramUpdateInbox;
import com.billing.simple.billsoft.telegram.repo.TelegramConfigRepository;
import com.billing.simple.billsoft.telegram.repo.TelegramUpdateInboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TelegramPollerService {

    private static final Logger log = LoggerFactory.getLogger(TelegramPollerService.class);

    private final TelegramConfigRepository configRepo;
    private final TelegramUpdateInboxRepository inboxRepo;
    private final TelegramApiClient apiClient;
    private final TelegramUpdateDispatcher dispatcher;
    private final TelegramDeliveryService deliveryService;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    // Transient in-memory bot token received via loopback bridge
    private volatile String inMemoryBotToken = null;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread pollerThread;
    private final ExecutorService workerExecutor = Executors.newFixedThreadPool(4);

    public TelegramPollerService(TelegramConfigRepository configRepo,
                                 TelegramUpdateInboxRepository inboxRepo,
                                 TelegramApiClient apiClient,
                                 TelegramUpdateDispatcher dispatcher,
                                 TelegramDeliveryService deliveryService,
                                 PlatformTransactionManager transactionManager,
                                 ObjectMapper objectMapper) {
        this.configRepo = configRepo;
        this.inboxRepo = inboxRepo;
        this.apiClient = apiClient;
        this.dispatcher = dispatcher;
        this.deliveryService = deliveryService;
        this.transactionManager = transactionManager;
        this.objectMapper = objectMapper;
    }

    public synchronized void setBotTokenAndStart(String token) {
        this.inMemoryBotToken = token;
        TelegramConfig config = getOrCreateConfig();
        config.setIsEnabled(true);
        config.setCircuitBreakerTripped(false);
        config.setConflictCount(0);
        configRepo.save(config);

        startPoller();
    }

    public synchronized void stopPoller() {
        running.set(false);
        if (pollerThread != null) {
            pollerThread.interrupt();
            pollerThread = null;
        }
        log.info("Telegram long-polling worker stopped.");
    }

    @PreDestroy
    public synchronized void onDestroy() {
        stopPoller();
        workerExecutor.shutdown();
        try {
            if (!workerExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                workerExecutor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            workerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void startPoller() {
        if (running.get()) {
            return;
        }
        if (inMemoryBotToken == null || inMemoryBotToken.isBlank()) {
            log.info("Telegram poller not started: bot token not configured in memory.");
            return;
        }
        TelegramConfig config = getOrCreateConfig();
        if (Boolean.TRUE.equals(config.getCircuitBreakerTripped())) {
            log.warn("Telegram poller circuit breaker tripped. Manual resume required.");
            return;
        }

        running.set(true);
        pollerThread = new Thread(this::pollLoop, "telegram-poller-daemon");
        pollerThread.setDaemon(true);
        pollerThread.start();
        log.info("Telegram long-polling worker daemon started.");
    }

    public synchronized void resumeCircuitBreaker() {
        TelegramConfig config = getOrCreateConfig();
        config.setCircuitBreakerTripped(false);
        config.setConflictCount(0);
        config.setLastError(null);
        configRepo.save(config);
        startPoller();
    }

    public boolean isRunning() {
        return running.get();
    }

    public TelegramConfig getConfig() {
        return getOrCreateConfig();
    }

    private TelegramConfig getOrCreateConfig() {
        return configRepo.findById(1L).orElseGet(() -> {
            TelegramConfig cfg = new TelegramConfig();
            cfg.setId(1L);
            cfg.setInstallationId(UUID.randomUUID().toString());
            cfg.setIsEnabled(false);
            cfg.setLastPollingOffset(0L);
            cfg.setPollingTimeoutSeconds(30);
            cfg.setCircuitBreakerTripped(false);
            cfg.setConflictCount(0);
            return configRepo.save(cfg);
        });
    }

    public String getInMemoryBotToken() {
        return inMemoryBotToken;
    }

    public void recordSentDiagnostic() {
        try {
            TelegramConfig cfg = getOrCreateConfig();
            cfg.setLastSentAt(LocalDateTime.now());
            configRepo.save(cfg);
        } catch (Exception ignored) {}
    }

    private void recordProcessedDiagnostic(Long updateId) {
        try {
            TelegramConfig cfg = getOrCreateConfig();
            cfg.setLastProcessedAt(LocalDateTime.now());
            configRepo.save(cfg);
        } catch (Exception ignored) {}
    }

    private void pollLoop() {
        int backoffSeconds = 2;

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            String token = inMemoryBotToken;
            if (token == null || token.isBlank()) {
                break;
            }

            try {
                TelegramConfig config = getOrCreateConfig();
                config.setLastPollAt(LocalDateTime.now());
                configRepo.save(config);

                Long currentOffset = config.getLastPollingOffset();

                // 1. Fetch batch from Telegram API
                JsonNode response = apiClient.getUpdates(token, currentOffset > 0 ? currentOffset : null, config.getPollingTimeoutSeconds());

                if (response != null && response.path("ok").asBoolean()) {
                    JsonNode result = response.path("result");

                    if (result.isArray() && result.size() > 0) {
                        // 2. Atomic Ingestion Transaction: batch updates + advance offset
                        ingestBatchAtomically(result);
                    }

                    // Reset backoff & conflict count on success
                    backoffSeconds = 2;
                    config = getOrCreateConfig();
                    config.setConflictCount(0);
                    config.setLastActiveAt(LocalDateTime.now());
                    config.setLastError(null);
                    configRepo.save(config);

                    // 3. Process inbox records asynchronously
                    dispatchPendingInboxRecords();

                    // 4. Process pending document deliveries
                    deliveryService.processPendingDeliveries(token);
                }

            } catch (TelegramApiClient.TelegramConflictException e) {
                log.error("Telegram 409 conflict detected: another instance is polling this token.");
                handleConflict409();
                break;

            } catch (Exception e) {
                if (!running.get() || Thread.currentThread().isInterrupted()) {
                    break;
                }
                log.warn("Telegram poll encountered network or IO exception: {}. Backing off {}s.", e.getMessage(), backoffSeconds);
                TelegramConfig cfg = getOrCreateConfig();
                cfg.setLastError(e.getMessage());
                configRepo.save(cfg);

                try {
                    TimeUnit.SECONDS.sleep(backoffSeconds);
                    backoffSeconds = Math.min(backoffSeconds * 2, 60);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        running.set(false);
    }

    private void ingestBatchAtomically(JsonNode updatesArray) {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        TransactionStatus status = transactionManager.getTransaction(def);

        try {
            Long maxUpdateId = null;

            for (JsonNode update : updatesArray) {
                Long updateId = update.path("update_id").asLong();
                if (maxUpdateId == null || updateId > maxUpdateId) {
                    maxUpdateId = updateId;
                }

                // Deduplicate: check if updateId already exists
                if (!inboxRepo.existsById(updateId)) {
                    TelegramUpdateInbox item = new TelegramUpdateInbox();
                    item.setUpdateId(updateId);
                    item.setPayloadJson(update.toString());
                    item.setProcessingStatus("RECEIVED");
                    item.setAttemptCount(0);
                    item.setReceivedAt(LocalDateTime.now());

                    if (update.has("message")) {
                        item.setUpdateType("MESSAGE");
                        item.setChatId(update.path("message").path("chat").path("id").asLong());
                        item.setUserId(update.path("message").path("from").path("id").asLong());
                    } else if (update.has("callback_query")) {
                        item.setUpdateType("CALLBACK_QUERY");
                        item.setChatId(update.path("callback_query").path("message").path("chat").path("id").asLong());
                        item.setUserId(update.path("callback_query").path("from").path("id").asLong());
                    }

                    inboxRepo.save(item);
                }
            }

            // Advance offset durably in the SAME transaction
            if (maxUpdateId != null) {
                TelegramConfig cfg = configRepo.findById(1L).orElseGet(TelegramConfig::new);
                cfg.setLastPollingOffset(maxUpdateId + 1);
                cfg.setLastUpdateId(maxUpdateId);
                cfg.setUpdatedAt(LocalDateTime.now());
                configRepo.save(cfg);
            }

            transactionManager.commit(status);

        } catch (Exception e) {
            transactionManager.rollback(status);
            log.error("Failed atomic ingestion transaction for updates: {}", e.getMessage(), e);
            throw new RuntimeException("Rollback update ingestion batch", e);
        }
    }

    private void dispatchPendingInboxRecords() {
        List<TelegramUpdateInbox> pending;
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        TransactionStatus status = transactionManager.getTransaction(def);
        try {
            // Recover orphaned leases on startup or crashes
            List<TelegramUpdateInbox> orphaned = inboxRepo.findOrphanedLeasedUpdates(LocalDateTime.now());
            for (TelegramUpdateInbox o : orphaned) {
                o.setProcessingStatus("RECEIVED");
                o.setLeaseExpiresAt(null);
                inboxRepo.save(o);
            }

            inboxRepo.markExhaustedLeasesDeadLetter(LocalDateTime.now());

            pending = inboxRepo.findTop50ByProcessingStatusOrderByUpdateIdAsc("RECEIVED");
            LocalDateTime lease = LocalDateTime.now().plusMinutes(2);
            for (TelegramUpdateInbox record : pending) {
                inboxRepo.claimLease(record.getUpdateId(), lease);
            }
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
            log.error("Failed transaction claiming leases in dispatchPendingInboxRecords: {}", e.getMessage(), e);
            return;
        }

        final String token = inMemoryBotToken;
        final String instId = getOrCreateConfig().getInstallationId();

        for (TelegramUpdateInbox record : pending) {
            workerExecutor.submit(() -> {
                try {
                    JsonNode payload = objectMapper.readTree(record.getPayloadJson());
                    dispatcher.dispatch(payload, token, instId);

                    DefaultTransactionDefinition txDef = new DefaultTransactionDefinition();
                    TransactionStatus txStatus = transactionManager.getTransaction(txDef);
                    try {
                        inboxRepo.updateInboxStatus(record.getUpdateId(), "PROCESSED", LocalDateTime.now(), null);
                        transactionManager.commit(txStatus);
                    } catch (Exception ex) {
                        transactionManager.rollback(txStatus);
                        log.error("Failed to commit PROCESSED status for update {}: {}", record.getUpdateId(), ex.getMessage());
                    }

                    recordProcessedDiagnostic(record.getUpdateId());
                } catch (Exception e) {
                    log.error("Failed processing inbox record {}: {}", record.getUpdateId(), e.getMessage());
                    try {
                        DefaultTransactionDefinition txDef = new DefaultTransactionDefinition();
                        TransactionStatus txStatus = transactionManager.getTransaction(txDef);
                        inboxRepo.updateInboxStatus(record.getUpdateId(), "FAILED", LocalDateTime.now(), e.getMessage());
                        transactionManager.commit(txStatus);
                    } catch (Exception ex) {
                        log.error("Failed to record FAILED status for update {}: {}", record.getUpdateId(), ex.getMessage());
                    }
                }
            });
        }
    }

    private void handleConflict409() {
        TelegramConfig config = getOrCreateConfig();
        int count = config.getConflictCount() + 1;
        config.setConflictCount(count);
        config.setCircuitBreakerTripped(true);
        config.setCircuitBreakerReason("409 Conflict: another RupeeCRM instance is polling this bot token.");
        config.setLastError("Halted: 409 Conflict. Another RupeeCRM instance is polling this bot token.");
        configRepo.save(config);
        log.error("Telegram circuit breaker tripped: another RupeeCRM instance is polling token.");
    }
}
