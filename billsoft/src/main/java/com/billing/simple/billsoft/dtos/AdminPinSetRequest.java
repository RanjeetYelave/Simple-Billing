package com.billing.simple.billsoft.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPinSetRequest {
    private String oldPin;
    private String newPin;
}

