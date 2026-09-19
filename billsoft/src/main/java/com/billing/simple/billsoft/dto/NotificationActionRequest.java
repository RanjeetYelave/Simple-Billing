package com.billing.simple.billsoft.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationActionRequest {
    /**
     * "PRIMARY" or "SECONDARY"
     */
    private String actionChoice;

    /**
     * Optional payload for API_ACTION (e.g. status value or confirmation)
     */
    private String payload;
}
