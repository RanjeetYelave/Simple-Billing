package com.billing.simple.billsoft.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyLinkCandidateDto {
    private Long id;
    private String type; // "CUSTOMER" or "PARTY"
    private String name;
    private String phone;
    private String gstin;
    private String address;
    private String city;
    private String state;
    private String contactPerson;
    private Double balance;
    private String balanceStatus; // "RECEIVABLE" or "PAYABLE" / "ADVANCE" / "SETTLED"
}
