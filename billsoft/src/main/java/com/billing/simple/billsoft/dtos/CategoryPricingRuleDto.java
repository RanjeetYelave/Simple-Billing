package com.billing.simple.billsoft.dtos;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryPricingRuleDto {
    private Long id;
    private Long firmId;
    private String categoryName;
    private Long pricingProfileId;
    private String pricingProfileCode;
    private String pricingProfileName;
    @Builder.Default
    private String operator = "+"; // +, -, *, / (defaults to +)
    private String adjustmentType; // PERCENT or FIXED
    private BigDecimal adjustmentValue; // e.g. 5.00 (+5%), -10.00 (-10%), 500.00 (+₹500), -250.00 (-₹250)
}
