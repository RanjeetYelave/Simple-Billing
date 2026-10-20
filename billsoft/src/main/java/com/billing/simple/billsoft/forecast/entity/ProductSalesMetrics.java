package com.billing.simple.billsoft.forecast.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pre-aggregated daily product sales, demand, price, and customer metrics.
 * Ensures fast UI dashboard rendering and clean daily time-series feeding.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "product_sales_metrics", indexes = {
    @Index(name = "idx_psm_firm_prod_date", columnList = "firmId, productId, metricDate", unique = true),
    @Index(name = "idx_psm_firm_date", columnList = "firmId, metricDate")
})
public class ProductSalesMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private LocalDate metricDate;

    // Gross requested customer demand
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal grossRequestedQty = BigDecimal.ZERO;

    // Gross physically fulfilled demand
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal grossFulfilledQty = BigDecimal.ZERO;

    // Shortage / unfulfilled customer demand
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal shortageQty = BigDecimal.ZERO;

    // Returned quantity
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal returnedQty = BigDecimal.ZERO;

    // Net requested customer demand (grossRequestedQty - returnedQty)
    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal netRequestedQty = BigDecimal.ZERO;

    // Gross tax-inclusive sales revenue
    @Builder.Default
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal grossSales = BigDecimal.ZERO;

    // Total refund amount from returns
    @Builder.Default
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    // Net tax-inclusive sales revenue (grossSales - refundAmount)
    @Builder.Default
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal netSales = BigDecimal.ZERO;

    // Transaction-time realized unit price (grossSales / grossRequestedQty)
    @Column(precision = 15, scale = 2)
    private BigDecimal transactionTimeRealizedPrice;

    // Net realized price (netSales / netRequestedQty)
    @Column(precision = 15, scale = 2)
    private BigDecimal netRealizedPrice;

    // Lowest historical realized unit price on this date
    @Column(precision = 15, scale = 2)
    private BigDecimal minRealizedPrice;

    // Highest historical realized unit price on this date
    @Column(precision = 15, scale = 2)
    private BigDecimal maxRealizedPrice;

    // Count of unique identified customers (customer_id != null)
    @Builder.Default
    @Column(nullable = false)
    private Integer uniqueCustomerCount = 0;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void onSave() {
        this.updatedAt = LocalDateTime.now();
    }
}
