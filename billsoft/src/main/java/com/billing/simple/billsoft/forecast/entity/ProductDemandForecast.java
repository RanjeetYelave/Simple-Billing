package com.billing.simple.billsoft.forecast.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Immutable daily forecasted customer demand trajectory point (T+1 ... T+365).
 * Cumulative horizons (30d, 90d, 180d, 365d) are derived directly from these daily points.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "product_demand_forecast", indexes = {
    @Index(name = "idx_pdf_firm_prod_date", columnList = "firmId, productId, forecastDate"),
    @Index(name = "idx_pdf_model_run", columnList = "modelRunId")
})
public class ProductDemandForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long modelRunId;

    // Target future projection date
    @Column(nullable = false)
    private LocalDate forecastDate;

    // Predicted customer demand quantity
    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal predictedDemand;

    // Deterministic confidence score (0 - 100)
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    // "HIGH", "MEDIUM", "LOW"
    @Column(nullable = false, length = 16)
    private String confidenceLevel;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
