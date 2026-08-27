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
@Table(name = "notes")
public class Note {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Firm this note is associated with
    private Long firmId;

    // Optional link to customer
    private Long customerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String content;

    private String tags; // Comma-separated values

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
