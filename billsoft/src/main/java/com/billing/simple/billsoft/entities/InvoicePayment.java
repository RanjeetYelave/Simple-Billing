package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "invoice_payments")
public class InvoicePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private Long invoiceId;

    private Long customerId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(length = 50)
    private String paymentMode; // Cash, UPI, Bank Transfer, Card, Cheque

    @Column(length = 100)
    private String referenceNumber;

    @Column(length = 1000)
    private String notes;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (paymentDate == null) {
            paymentDate = LocalDate.now();
        }
    }
}
