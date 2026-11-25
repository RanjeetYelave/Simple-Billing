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
    private boolean inProgress;
    private boolean isFresh;
    private LocalDateTime lastSyncCompletedAt;
    private String lastSyncStatus;
    private String message;
    private long durationMs;
}
