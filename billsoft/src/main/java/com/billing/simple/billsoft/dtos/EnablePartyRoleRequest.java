package com.billing.simple.billsoft.dtos;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnablePartyRoleRequest {
    private Long customerId;
    private Long targetPartyId;
    private Boolean confirmCreateNew;
    private String partyName;
    private String phone;
    private String email;
    private String address;
    private String gstin;
    private String contactPerson;
    private String city;
    private String state;
    private String pincode;
    private String pan;
    private String bankName;
    private String bankAccount;
    private String bankIfsc;
    private String upiId;
    private BigDecimal openingBalance;
    private String openingBalanceType;
    private String notes;
    private String linkedBy;
}

