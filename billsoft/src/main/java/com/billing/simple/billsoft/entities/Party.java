package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a Party (Vendor / Supplier / Contractor) from whom the firm orders or purchases goods/services.
 * Completely distinct from Customer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "parties")
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private String name;

    private String contactPerson;

    @Column(length = 20)
    private String phone;

    private String email;

    @Column(length = 500)
    private String address;

    private String city;

    private String state;

    @Column(length = 10)
    private String pincode;

    @Column(length = 20)
    private String gstin;

    @Column(length = 20)
    private String pan;

    private String bankName;

    private String bankAccount;

    private String bankIfsc;

    private String upiId;

    @Builder.Default
    @Column(precision = 15, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    // "PAYABLE" (we owe them / debt) or "ADVANCE" (we paid extra / credit)
    @Builder.Default
    @Column(length = 20)
    private String openingBalanceType = "PAYABLE";

    @Column(length = 1000)
    private String notes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.openingBalance == null) {
            this.openingBalance = BigDecimal.ZERO;
        }
        if (this.openingBalanceType == null || this.openingBalanceType.trim().isEmpty()) {
            this.openingBalanceType = "PAYABLE";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
