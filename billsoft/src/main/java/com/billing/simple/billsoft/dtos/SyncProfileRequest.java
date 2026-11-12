package com.billing.simple.billsoft.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncProfileRequest {
    private String source; // "CUSTOMER" or "PARTY"
    private String name;
    private String phone;
    private String email;
    private String address;
    private String gstin;
}

