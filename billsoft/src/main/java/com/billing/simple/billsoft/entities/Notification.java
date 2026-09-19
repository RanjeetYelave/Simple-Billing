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
@Table(
    name = "notifications",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_notifications_firm_event_key", columnNames = {"firmId", "eventKey"})
    },
    indexes = {
        @Index(name = "idx_notifications_firm_status_created", columnList = "firmId, status, createdAt"),
        @Index(name = "idx_notifications_event_key", columnList = "eventKey")
    }
)
public class Notification {

    public static final Long GLOBAL_FIRM_ID = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Builder.Default
    private Long firmId = GLOBAL_FIRM_ID;

    @Column(nullable = false, length = 255)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private NotificationCategory category = NotificationCategory.SYSTEM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 4000)
    private String body;

    @Column(length = 100)
    private String sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.UNREAD;

    @Column(length = 100)
    private String primaryActionLabel;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private NotificationActionType primaryActionType;

    @Column(length = 1000)
    private String primaryActionTarget;

    @Column(length = 100)
    private String secondaryActionLabel;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private NotificationActionType secondaryActionType;

    @Column(length = 1000)
    private String secondaryActionTarget;

    private LocalDateTime snoozedUntil;

    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (firmId == null) {
            firmId = GLOBAL_FIRM_ID;
        }
        if (status == null) {
            status = NotificationStatus.UNREAD;
        }
        if (category == null) {
            category = NotificationCategory.SYSTEM;
        }
        if (priority == null) {
            priority = NotificationPriority.NORMAL;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
