package com.billing.simple.billsoft.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessPartyLinkRequest {
    private Long customerId;
    private Long partyId;
    private String notes;
    private String linkedBy;
}
