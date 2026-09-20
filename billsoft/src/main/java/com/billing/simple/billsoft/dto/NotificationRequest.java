package com.billing.simple.billsoft.dto;

import com.billing.simple.billsoft.entities.NotificationActionType;
import com.billing.simple.billsoft.entities.NotificationCategory;
import com.billing.simple.billsoft.entities.NotificationPriority;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequest {
    private Long firmId;
    private String eventKey;
    private NotificationCategory category;
    private NotificationPriority priority;
    private String title;
    private String body;
    private String sender;
    private String primaryActionLabel;
    private NotificationActionType primaryActionType;
    private String primaryActionTarget;
    private String secondaryActionLabel;
    private NotificationActionType secondaryActionType;
    private String secondaryActionTarget;
    private LocalDateTime expiresAt;
}
