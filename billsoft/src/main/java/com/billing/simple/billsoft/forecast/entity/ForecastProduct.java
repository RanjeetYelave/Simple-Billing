package com.billing.simple.billsoft.forecast.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Stores product-level forecasting and restocking configuration.
 * Fully isolated from existing billing and product entities.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "forecast_product", indexes = {
    @Index(name = "idx_fp_firm_prod", columnList = "firmId, productId", unique = true)
})
public class ForecastProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private Long productId;

    @Builder.Default
    @Column(nullable = false)
    private Boolean forecastEnabled = true;

    // Lead time in days (Nullable: falls back to firm default -> technical fallback 7)
    private Integer leadTimeDays;

    // Review period in days (Nullable: falls back to firm default -> technical fallback 7)
    private Integer reviewPeriodDays;

    // Minimum Order Quantity floor (Nullable: default fallback 1.000)
    @Column(precision = 12, scale = 3)
    private BigDecimal minimumOrderQty;

    // Pack size multiple ceiling (Nullable: default fallback 1.000)
    @Column(precision = 12, scale = 3)
    private BigDecimal packSize;

    // Manual safety stock override (Nullable: if set, overrides calculated safety stock)
    @Column(precision = 12, scale = 3)
    private BigDecimal safetyStockOverride;

    // Manual model selection override (Nullable: e.g. "CROSTON", "SES", "HOLT_WINTERS")
    @Column(length = 64)
    private String modelOverride;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.forecastEnabled == null) this.forecastEnabled = true;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
