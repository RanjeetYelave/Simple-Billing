package com.billing.simple.billsoft.dtos;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessPartyLinkResponse {
    private Long id;
    private Long firmId;
    private Long customerId;
    private Long partyId;
    private LocalDateTime linkedAt;
    private String linkedBy;
    private String notes;

    // Customer Snapshot / Context
    private String customerName;
    private String customerPhone;
    private String customerGstin;
    private String customerAddress;
    private Double customerNetReceivable;

    // Party Snapshot / Context
    private String partyName;
    private String partyPhone;
    private String partyGstin;
    private String partyAddress;
    private String partyContactPerson;
    private Double partyNetPayable;
    private String partyBalanceStatus;
}
