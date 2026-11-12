package com.billing.simple.billsoft.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CanonicalNameUpdateRequest {
    private String canonicalName;
    private String reason;
    private String updatedBy;
}

