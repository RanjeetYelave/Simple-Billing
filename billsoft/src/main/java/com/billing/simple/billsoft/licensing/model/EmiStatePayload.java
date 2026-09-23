package com.billing.simple.billsoft.licensing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EmiStatePayload {
    private BigDecimal paidAmount;
    private BigDecimal outstandingAmount;
    private Integer currentInstallment;
    private String nextDueDate;
    private String graceDeadline;
    private String emiStatus;
    private String accessStatus;

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public BigDecimal getOutstandingAmount() { return outstandingAmount; }
    public void setOutstandingAmount(BigDecimal outstandingAmount) { this.outstandingAmount = outstandingAmount; }
    public Integer getCurrentInstallment() { return currentInstallment; }
    public void setCurrentInstallment(Integer currentInstallment) { this.currentInstallment = currentInstallment; }
    public String getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(String nextDueDate) { this.nextDueDate = nextDueDate; }
    public String getGraceDeadline() { return graceDeadline; }
    public void setGraceDeadline(String graceDeadline) { this.graceDeadline = graceDeadline; }
    public String getEmiStatus() { return emiStatus; }
    public void setEmiStatus(String emiStatus) { this.emiStatus = emiStatus; }
    public String getAccessStatus() { return accessStatus; }
    public void setAccessStatus(String accessStatus) { this.accessStatus = accessStatus; }
}
