package com.billing.simple.billsoft.forecast.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Normalized transaction fact representing discrete SALE and RETURN lines.
 * Uses (firmId, sourceType, sourceHeaderId, sourceLineOrdinal) as the stable natural key.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "forecast_sales_transaction", indexes = {
    @Index(name = "idx_fst_firm_source", columnList = "firmId, sourceType, sourceHeaderId, sourceLineOrdinal", unique = true),
    @Index(name = "idx_fst_firm_prod_date", columnList = "firmId, productId, transactionDate"),
    @Index(name = "idx_fst_firm_date", columnList = "firmId, transactionDate")
})
public class ForecastSalesTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    // "SALE" or "RETURN"
    @Column(nullable = false, length = 16)
    private String sourceType;

    // invoice.id (for SALE) or sales_return.id (for RETURN)
    @Column(nullable = false)
    private Long sourceHeaderId;

    // 0-based sequence ordinal within header
    @Column(nullable = false)
    private Integer sourceLineOrdinal;

    // product.id (Nullable for ad-hoc custom items)
    private Long productId;

    // customer.id (Nullable for walk-in / anonymous customers)
    private Long customerId;

    // invoiceDate (for SALE) or returnDate (for RETURN)
    @Column(nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false)
    private LocalDateTime transactionTimestamp;

    // Requested customer demand or return quantity
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal requestedQty;

    // Physical quantity fulfilled (0 for RETURN, bounded by stock for physical goods)
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal fulfilledQty;

    // Shortage = max(0, requestedQty - fulfilledQty)
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal shortageQty;

    // Realized gross unit price = lineTotal / requestedQty
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    // Ex-tax line taxable amount
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal taxableAmount;

    // Line tax amount
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount;

    // Tax-inclusive line total / refund total
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    // Invoice status (e.g. "PAID", "UNPAID", "FINAL"; null for RETURN)
    @Column(length = 32)
    private String invoiceStatus;

    // Timestamp when source record was last modified
    @Column(nullable = false)
    private LocalDateTime sourceUpdatedAt;
}
