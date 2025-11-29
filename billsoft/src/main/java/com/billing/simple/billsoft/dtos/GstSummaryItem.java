package com.billing.simple.billsoft.dtos;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * GST summary row used inside FirmStatementResponse.
 */
@Getter
@Setter
@NoArgsConstructor
public class GstSummaryItem {

    private Double gstPercent;
    private Double taxableValue;
    private Double gstAmount;
}
