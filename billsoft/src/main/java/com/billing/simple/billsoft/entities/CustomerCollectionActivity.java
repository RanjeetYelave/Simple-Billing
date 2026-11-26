package com.billing.simple.billsoft.entities;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "customer_collection_activities", indexes = {
    @Index(name = "idx_activity_firm_customer", columnList = "firmId, customerId"),
    @Index(name = "idx_activity_firm_created", columnList = "firmId, createdAt")
})
public class CustomerCollectionActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private Long customerId;

    private Long invoiceId;
    private Long paymentId;
    private Long promiseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CollectionActivityType activityType;

    @Column(length = 2000)
    private String description;

    @Column(length = 100)
    private String performedBy;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
