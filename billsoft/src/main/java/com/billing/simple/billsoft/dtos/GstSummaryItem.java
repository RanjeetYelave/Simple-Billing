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

    public Double getGstPercent() { return gstPercent; }
    public void setGstPercent(Double gstPercent) { this.gstPercent = gstPercent; }

    public Double getTaxableValue() { return taxableValue; }
    public void setTaxableValue(Double taxableValue) { this.taxableValue = taxableValue; }

    public Double getGstAmount() { return gstAmount; }
    public void setGstAmount(Double gstAmount) { this.gstAmount = gstAmount; }
}

