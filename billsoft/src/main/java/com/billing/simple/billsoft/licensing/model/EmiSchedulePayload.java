package com.billing.simple.billsoft.licensing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EmiSchedulePayload {
    private Integer tenureMonths;
    private String interval;
    private String firstDueDate;
    private Integer graceDays;
    private BigDecimal installmentAmount;

    public Integer getTenureMonths() { return tenureMonths; }
    public void setTenureMonths(Integer tenureMonths) { this.tenureMonths = tenureMonths; }
    public String getInterval() { return interval; }
    public void setInterval(String interval) { this.interval = interval; }
    public String getFirstDueDate() { return firstDueDate; }
    public void setFirstDueDate(String firstDueDate) { this.firstDueDate = firstDueDate; }
    public Integer getGraceDays() { return graceDays; }
    public void setGraceDays(Integer graceDays) { this.graceDays = graceDays; }
    public BigDecimal getInstallmentAmount() { return installmentAmount; }
    public void setInstallmentAmount(BigDecimal installmentAmount) { this.installmentAmount = installmentAmount; }
}
