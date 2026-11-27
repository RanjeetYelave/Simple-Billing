package com.billing.simple.billsoft.telegram.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_config")
public class TelegramConfig {

    @Id
    private Long id = 1L;

    @Column(name = "bot_username", length = 100)
    private String botUsername;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = false;

    @Column(name = "polling_timeout_seconds", nullable = false)
    private Integer pollingTimeoutSeconds = 30;

    @Column(name = "last_polling_offset", nullable = false)
    private Long lastPollingOffset = 0L;

    @Column(name = "circuit_breaker_tripped", nullable = false)
    private Boolean circuitBreakerTripped = false;

    @Column(name = "conflict_count", nullable = false)
    private Integer conflictCount = 0;

    @Column(name = "installation_id", length = 64)
    private String installationId;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "circuit_breaker_reason", length = 500)
    private String circuitBreakerReason;

    @Column(name = "last_poll_at")
    private LocalDateTime lastPollAt;

    @Column(name = "last_update_id")
    private Long lastUpdateId;

    @Column(name = "last_processed_at")
    private LocalDateTime lastProcessedAt;

    @Column(name = "last_sent_at")
    private LocalDateTime lastSentAt;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public TelegramConfig() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBotUsername() { return botUsername; }
    public void setBotUsername(String botUsername) { this.botUsername = botUsername; }

    public Boolean getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Boolean enabled) { isEnabled = enabled; }

    public Integer getPollingTimeoutSeconds() { return pollingTimeoutSeconds; }
    public void setPollingTimeoutSeconds(Integer pollingTimeoutSeconds) { this.pollingTimeoutSeconds = pollingTimeoutSeconds; }

    public Long getLastPollingOffset() { return lastPollingOffset; }
    public void setLastPollingOffset(Long lastPollingOffset) { this.lastPollingOffset = lastPollingOffset; }

    public Boolean getCircuitBreakerTripped() { return circuitBreakerTripped; }
    public void setCircuitBreakerTripped(Boolean circuitBreakerTripped) { this.circuitBreakerTripped = circuitBreakerTripped; }

    public Integer getConflictCount() { return conflictCount; }
    public void setConflictCount(Integer conflictCount) { this.conflictCount = conflictCount; }

    public String getInstallationId() { return installationId; }
    public void setInstallationId(String installationId) { this.installationId = installationId; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public String getCircuitBreakerReason() { return circuitBreakerReason; }
    public void setCircuitBreakerReason(String circuitBreakerReason) { this.circuitBreakerReason = circuitBreakerReason; }

    public LocalDateTime getLastPollAt() { return lastPollAt; }
    public void setLastPollAt(LocalDateTime lastPollAt) { this.lastPollAt = lastPollAt; }

    public Long getLastUpdateId() { return lastUpdateId; }
    public void setLastUpdateId(Long lastUpdateId) { this.lastUpdateId = lastUpdateId; }

    public LocalDateTime getLastProcessedAt() { return lastProcessedAt; }
    public void setLastProcessedAt(LocalDateTime lastProcessedAt) { this.lastProcessedAt = lastProcessedAt; }

    public LocalDateTime getLastSentAt() { return lastSentAt; }
    public void setLastSentAt(LocalDateTime lastSentAt) { this.lastSentAt = lastSentAt; }

    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
