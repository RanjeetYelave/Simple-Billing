package com.billing.simple.billsoft.dtos;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditTrailEventDto {
    private Long id;
    private Long firmId;
    private Long linkId;
    private Integer relationshipVersion;
    private Long customerId;
    private Long partyId;
    private String action;
    private String actor;
    private LocalDateTime timestamp;
    private String beforeValue;
    private String afterValue;
    private String reason;
}

