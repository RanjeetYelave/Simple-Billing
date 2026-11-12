package com.billing.simple.billsoft.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnableCustomerRoleRequest {
    private Long partyId;
    private Long targetCustomerId;
    private Boolean confirmCreateNew;
    private String customerName;
    private String phone;
    private String email;
    private String address;
    private String gstin;
    private String notes;
    private String linkedBy;
}

