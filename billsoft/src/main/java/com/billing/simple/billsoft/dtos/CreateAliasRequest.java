package com.billing.simple.billsoft.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAliasRequest {
    private String aliasValue;
    private String aliasType; // "MANUAL_ALIAS", "TRADE_NAME", "LEGAL_NAME"
    private String ownerRole; // "SHARED", "CUSTOMER", "PARTY"
    private String createdBy;
    private String notes;
}

