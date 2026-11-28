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
@Table(name = "customer_promise_history", indexes = {
    @Index(name = "idx_cph_firm_promise", columnList = "firmId, promiseId"),
    @Index(name = "idx_cph_created", columnList = "createdAt")
})
public class CustomerPromiseHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private Long promiseId;

    @Column(nullable = false, length = 50)
    private String actionType; // CREATED, EDITED, CANCELLED, ALLOCATED, ALLOCATION_REVERSED, RECONCILED

    @Column(precision = 15, scale = 2)
    private BigDecimal previousAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal newAmount;

    private LocalDate previousDate;
    private LocalDate newDate;

    @Column(length = 50)
    private String previousStatus;

    @Column(length = 50)
    private String newStatus;

    @Column(precision = 15, scale = 2)
    private BigDecimal allocatedAmount;

    private Long paymentId;

    @Column(length = 1000)
    private String notes;

    @Column(length = 100)
    private String performedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
