package com.billing.simple.billsoft.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreferencesDto {
    private boolean enabled;
    private boolean bellEnabled;
    private boolean inboxEnabled;
    private String defaultSnooze;
    private boolean licensingEnabled;
    private boolean billingEnabled;
    private boolean inventoryEnabled;
    private boolean purchaseEnabled;
    private boolean plannerEnabled;
    private boolean hrEnabled;
    private boolean systemEnabled;
}
