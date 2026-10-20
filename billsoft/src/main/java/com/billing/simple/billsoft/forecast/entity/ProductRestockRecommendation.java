package com.billing.simple.billsoft.forecast.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Persisted snapshot of an advisory restock recommendation.
 * Contains complete explainability telemetry for why an order quantity was suggested.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "product_restock_recommendation", indexes = {
    @Index(name = "idx_prr_firm_prod_gen", columnList = "firmId, productId, generatedAt DESC"),
    @Index(name = "idx_prr_firm_status_gen", columnList = "firmId, recommendationStatus, generatedAt DESC")
})
public class ProductRestockRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long modelRunId;

    // Physical on-hand stock at generation time
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal currentStock;

    // Total on-order stock from Purchase Orders in ISSUED status
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal incomingStock;

    // Forecast demand during Lead Time
    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal leadTimeDemand;

    // Forecast demand during Review Period
    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal reviewPeriodDemand;

    // Total planning horizon demand (Lead Time + Review Period)
    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal totalHorizonDemand;

    // Effective safety stock buffer
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal safetyStock;

    // Target stock level = totalHorizonDemand + safetyStock
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal targetStock;

    // Raw unconstrained recommendation = max(0, targetStock - (currentStock + incomingStock))
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal rawRecommendation;

    // Recommendation adjusted for Minimum Order Quantity floor
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal moqAdjustedRecommendation;

    // Final recommendation adjusted upward to valid Pack Size ceiling multiple
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal recommendedOrderQty;

    // Projected date when stock + incoming POs will be exhausted
    private LocalDate expectedStockoutDate;

    // "OUT_OF_STOCK", "CRITICAL", "REORDER_NEEDED", "HEALTHY"
    @Column(nullable = false, length = 32)
    private String urgencyStatus;

    // "HIGH", "MEDIUM", "LOW"
    @Column(nullable = false, length = 16)
    private String confidenceLevel;

    // "ACTIVE", "SUPERSEDED", "DISMISSED"
    @Builder.Default
    @Column(nullable = false, length = 32)
    private String recommendationStatus = "ACTIVE";

    // "PENDING", "PO_CREATED", "IGNORED"
    @Builder.Default
    @Column(nullable = false, length = 32)
    private String userActionState = "PENDING";

    // Textual human-readable explanation of the mathematical decision
    @Column(length = 2000)
    private String explanationText;

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    @PrePersist
    public void prePersist() {
        if (this.generatedAt == null) {
            this.generatedAt = LocalDateTime.now();
        }
    }
}
