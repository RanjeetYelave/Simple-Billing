package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "reminders")
public class Reminder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Firm the reminder belongs to
    @Column(nullable = false)
    private Long firmId;

    // Optional link to customer
    private Long customerId;

    // Optional link to invoice (or estimate)
    private Long invoiceId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String note;

    private LocalDateTime dueDate;

    private boolean completed = false;

    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
