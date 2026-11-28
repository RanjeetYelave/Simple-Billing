package com.billing.simple.billsoft.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "promise_payment_allocations", indexes = {
    @Index(name = "idx_ppa_firm_promise", columnList = "firmId, promiseId"),
    @Index(name = "idx_ppa_firm_payment", columnList = "firmId, paymentId")
})
public class PromisePaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private Long promiseId;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal allocatedAmount;

    @Column(nullable = false)
    private LocalDateTime allocatedAt;

    @Column(length = 100)
    private String allocatedBy;

    @PrePersist
    public void prePersist() {
        if (allocatedAt == null) {
            allocatedAt = LocalDateTime.now();
        }
    }
}
