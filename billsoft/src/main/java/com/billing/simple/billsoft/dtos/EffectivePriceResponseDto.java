package com.billing.simple.billsoft.dtos;

import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EffectivePriceResponseDto {
    private Long pricingProfileId;
    private String pricingProfileCode;
    private String pricingProfileName;
    private Map<Long, BigDecimal> effectivePrices; // Map<ProductId, CalculatedEffectivePrice>
}
