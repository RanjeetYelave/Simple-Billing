package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "expenses")
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private Double amount;

    @Column(length = 100)
    private String category; // e.g. Office Supplies, Rent & Facilities, Utilities, Salaries & Wages, Travel, Software & Tools, Marketing, Miscellaneous

    private LocalDate expenseDate;

    @Column(length = 50)
    private String paymentMode; // Cash, UPI, Bank Transfer, Card

    @Column(length = 1000)
    private String notes;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (expenseDate == null) {
            expenseDate = LocalDate.now();
        }
    }
}
