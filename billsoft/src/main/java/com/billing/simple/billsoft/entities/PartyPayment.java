package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a payment transaction made to a Party (Vendor / Supplier).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "party_payments")
public class PartyPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long partyId;

    @Column(nullable = false)
    private Long firmId;

    private Long purchaseOrderId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate paymentDate;

    // CASH, BANK_TRANSFER, UPI, CHEQUE, OTHER
    @Builder.Default
    @Column(length = 30)
    private String paymentMode = "BANK_TRANSFER";

    private String referenceNumber;

    @Column(length = 1000)
    private String notes;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.paymentDate == null) {
            this.paymentDate = LocalDate.now();
        }
    }
}
