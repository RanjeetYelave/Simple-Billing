package com.billing.simple.billsoft.licensing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EmiPayload {
    private Boolean enabled;
    private EmiPricingPayload pricing;
    private EmiSchedulePayload schedule;
    private EmiStatePayload state;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public EmiPricingPayload getPricing() { return pricing; }
    public void setPricing(EmiPricingPayload pricing) { this.pricing = pricing; }
    public EmiSchedulePayload getSchedule() { return schedule; }
    public void setSchedule(EmiSchedulePayload schedule) { this.schedule = schedule; }
    public EmiStatePayload getState() { return state; }
    public void setState(EmiStatePayload state) { this.state = state; }
}
