package com.billing.simple.billsoft.dtos;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnlinkRequest {
    private String adminPin;
    private String reason;
    private String unlinkedBy;
    /**
     * Map of aliasId -> outcome ("ASSIGN_CUSTOMER", "ASSIGN_PARTY", "RETIRE")
     */
    private Map<Long, String> aliasResolutions;
}

