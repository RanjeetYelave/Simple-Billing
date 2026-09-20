package com.billing.simple.billsoft.dto;

import com.billing.simple.billsoft.entities.Notification;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSummaryResponse {
    private long unreadCount;
    private long totalActiveCount;
    private List<Notification> items;
    private boolean bellEnabled;
    private boolean inboxEnabled;
}
