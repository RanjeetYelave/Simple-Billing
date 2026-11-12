package com.billing.simple.billsoft.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleLifecycleRequest {
    private String role; // "CUSTOMER" or "PARTY"
    private String reason;
}

