package com.billing.simple.billsoft.forecast.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForecastSyncStatusDto {
    private Long firmId;
    private Long jobId;
    private boolean inProgress;
    private boolean isFresh;
    private boolean isStalled;
    private String stage;
    private String stageText;
    private int progressPercent;
    private LocalDateTime lastSyncCompletedAt;
    private String lastSyncStatus;
    private String message;
    private long durationMs;
    private int invoicesProcessed;
    private int productsAggregated;
    private int forecastsGenerated;
    private int recommendationsGenerated;
    private LocalDateTime heartbeatAt;
    private Long secondsSinceHeartbeat;
}

