package com.billing.simple.billsoft.forecast.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persisted execution log of automatic and background forecast synchronization jobs.
 * Enforces firm-scoped durable job state, failure isolation, and freshness detection.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "forecast_sync_job_records", indexes = {
    @Index(name = "idx_fcjob_firm_status_time", columnList = "firmId, status, completedAt DESC")
})
public class ForecastSyncJobRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false, length = 30)
    private String status; // PENDING, RUNNING, COMPLETED, FAILED, SKIPPED_FRESH

    @Column(length = 50)
    private String stage; // INITIALIZING, INGESTING_SALES, AGGREGATING_METRICS, COMPUTING_FORECASTS, GENERATING_RECOMMENDATIONS, COMPLETED, FAILED

    @Column(length = 100)
    private String stageText;

    @Builder.Default
    private int progressPercent = 0;

    @Builder.Default
    private int invoicesProcessed = 0;

    @Builder.Default
    private int returnsProcessed = 0;

    @Builder.Default
    private int productsAggregated = 0;

    @Builder.Default
    private int forecastsGenerated = 0;

    @Builder.Default
    private int recommendationsGenerated = 0;

    @Builder.Default
    private long durationMs = 0L;

    @Column(length = 500)
    private String message;

    @Column(length = 2000)
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LocalDateTime heartbeatAt;

    @PrePersist
    public void prePersist() {
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
        if (this.heartbeatAt == null) {
            this.heartbeatAt = this.startedAt;
        }
        if (this.status == null) {
            this.status = "PENDING";
        }
        if (this.stage == null) {
            this.stage = "INITIALIZING";
        }
    }
}
