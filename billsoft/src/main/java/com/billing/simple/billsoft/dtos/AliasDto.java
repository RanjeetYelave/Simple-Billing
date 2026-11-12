package com.billing.simple.billsoft.dtos;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AliasDto {
    private Long id;
    private Long firmId;
    private Long linkId;
    private Long customerId;
    private Long partyId;
    private String aliasValue;
    private String normalizedValue;
    private String aliasType; // "PREVIOUS_NAME", "MANUAL_ALIAS", "TRADE_NAME", "LEGAL_NAME"
    private String ownerRole; // "CUSTOMER", "PARTY", "SHARED", "RETIRED"
    private Boolean active;
    private LocalDateTime createdAt;
    private String createdBy;
    private String resolutionNotes;
}

