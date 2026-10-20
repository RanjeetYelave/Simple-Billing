package com.billing.simple.billsoft.forecast.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable audit log recording model execution, demand classification, hyperparameters,
 * rolling-origin backtest error metrics, and duration.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "forecast_model_run", indexes = {
    @Index(name = "idx_fmr_firm_prod_time", columnList = "firmId, productId, runTimestamp DESC"),
    @Index(name = "idx_fmr_firm_time", columnList = "firmId, runTimestamp DESC")
})
public class ForecastModelRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private LocalDateTime runTimestamp;

    // e.g. "CROSTON", "TSB", "SES", "HOLT_WINTERS", "HISTORICAL_MEAN"
    @Column(nullable = false, length = 64)
    private String modelName;

    @Column(nullable = false, length = 32)
    private String modelVersion;

    // JSON representation of hyperparameter configuration
    @Column(length = 2000)
    private String hyperparametersJson;

    // Demand pattern: "SMOOTH", "INTERMITTENT", "ERRATIC", "LUMPY", "LIMITED"
    @Column(length = 32)
    private String demandPattern;

    // Average Demand Interval
    @Column(precision = 10, scale = 4)
    private BigDecimal adi;

    // Demand Coefficient of Variation squared
    @Column(precision = 10, scale = 4)
    private BigDecimal cv2;

    // Weighted Absolute Percentage Error from rolling-origin backtest
    @Column(precision = 10, scale = 4)
    private BigDecimal wape;

    // Root Mean Squared Error from backtest
    @Column(precision = 10, scale = 4)
    private BigDecimal rmse;

    // Mean Absolute Error from backtest
    @Column(precision = 10, scale = 4)
    private BigDecimal mae;

    // Number of historical daily observations used in training
    @Column(nullable = false)
    private Integer trainingDays;

    // Number of backtest cross-validation folds evaluated
    @Builder.Default
    @Column(nullable = false)
    private Integer backtestFolds = 0;

    // Execution time in milliseconds
    @Column(nullable = false)
    private Long executionDurationMs;

    // "SUCCESS", "FALLBACK", "FAILED"
    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 1000)
    private String statusMessage;
}
