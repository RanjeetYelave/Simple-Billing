package com.billing.simple.billsoft.forecast.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable historical record of a forecast prediction paired with subsequent actual demand realization.
 * Enables objective measurement of forecasting error (MAE, WAPE, Bias) across rolling forecast horizons.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "forecast_realization_log", indexes = {
    @Index(name = "idx_frl_firm_prod_target", columnList = "firmId, productId, targetDate"),
    @Index(name = "idx_frl_firm_target", columnList = "firmId, targetDate"),
    @Index(name = "idx_frl_firm_status", columnList = "firmId, evaluationStatus")
})
public class ForecastRealizationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private Long productId;

    private Long modelRunId;

    @Column(nullable = false)
    private LocalDate originDate;

    @Column(nullable = false)
    private LocalDate targetDate;

    @Column(nullable = false)
    private Integer horizonDays;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal predictedDemand;

    @Column(precision = 12, scale = 4)
    private BigDecimal actualDemand;

    @Column(precision = 12, scale = 4)
    private BigDecimal absoluteError;

    @Column(length = 50)
    private String modelName;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String evaluationStatus = "PENDING"; // "PENDING", "EVALUATED", "CENSORED_STOCKOUT"

    private LocalDateTime createdAt;
    private LocalDateTime evaluatedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
