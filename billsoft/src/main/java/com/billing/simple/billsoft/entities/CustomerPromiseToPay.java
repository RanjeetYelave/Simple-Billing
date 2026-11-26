package com.billing.simple.billsoft.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "customer_promises_to_pay", indexes = {
    @Index(name = "idx_promise_firm_customer", columnList = "firmId, customerId"),
    @Index(name = "idx_promise_firm_status", columnList = "firmId, status"),
    @Index(name = "idx_promise_firm_date", columnList = "firmId, promisedDate")
})
public class CustomerPromiseToPay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private Long customerId;

    private Long invoiceId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal promisedAmount;

    @Column(nullable = false)
    private LocalDate promisedDate;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal fulfilledAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PromiseStatus status = PromiseStatus.PROMISED;

    @Column(length = 1000)
    private String notes;

    @Column(length = 100)
    private String createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
        if (fulfilledAmount == null) {
            fulfilledAmount = BigDecimal.ZERO;
        }
        if (remainingAmount == null && promisedAmount != null) {
            remainingAmount = promisedAmount.subtract(fulfilledAmount);
        }
        if (status == null) {
            status = PromiseStatus.PROMISED;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
