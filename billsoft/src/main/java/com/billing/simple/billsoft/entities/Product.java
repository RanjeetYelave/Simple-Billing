package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Price as BigDecimal
    @Column(precision = 15, scale = 2)
    private BigDecimal price;

    private String unit; // pcs, kg, litre etc.

    // GST percentage as BigDecimal (e.g., 18.00)
    @Column(precision = 5, scale = 2)
    private BigDecimal gstPercentage;

    @Column(nullable = false)
    private Long firmId;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
