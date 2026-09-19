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
    name = "notification_preferences",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_preferences_firm", columnNames = {"firmId"})
    }
)
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean bellEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean inboxEnabled = true;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String defaultSnooze = "1d";

    @Column(nullable = false)
    @Builder.Default
    private boolean licensingEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean billingEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean inventoryEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean purchaseEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean plannerEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean hrEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean systemEnabled = true;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void onSave() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isCategoryEnabled(NotificationCategory category) {
        if (!enabled) return false;
        if (category == null) return true;
        switch (category) {
            case LICENSING: return licensingEnabled;
            case BILLING: return billingEnabled;
            case INVENTORY: return inventoryEnabled;
            case PURCHASE: return purchaseEnabled;
            case PLANNER: return plannerEnabled;
            case HR: return hrEnabled;
            case SYSTEM:
            default:
                return systemEnabled;
        }
    }
}
