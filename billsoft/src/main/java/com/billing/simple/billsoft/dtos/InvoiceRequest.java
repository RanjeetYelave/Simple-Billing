package com.billing.simple.billsoft.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.billing.simple.billsoft.entities.InvoiceStatus;

public class InvoiceRequest {

    private Long firmId;
    private Long customerId;
    private String notes;
    private List<InvoiceRequestItem> items;
    private Boolean paid;
    private Discount invoiceDiscount;
    private String invoiceNumber;
    private String estimateNumber;
    private InvoiceStatus status;
    private Long convertedInvoiceId;
    private LocalDate dueDate;
    private String customerNote;
    private String termsAndConditions;
    private String paymentMethod;
    private String currency = "INR";
    private Boolean roundOff;
    private String tags;
    private String invoiceDate;

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<InvoiceRequestItem> getItems() { return items; }
    public void setItems(List<InvoiceRequestItem> items) { this.items = items; }

    public Boolean getPaid() { return paid; }
    public void setPaid(Boolean paid) { this.paid = paid; }

    public Discount getInvoiceDiscount() { return invoiceDiscount; }
    public void setInvoiceDiscount(Discount invoiceDiscount) { this.invoiceDiscount = invoiceDiscount; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getEstimateNumber() { return estimateNumber; }
    public void setEstimateNumber(String estimateNumber) { this.estimateNumber = estimateNumber; }

    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }

    public Long getConvertedInvoiceId() { return convertedInvoiceId; }
    public void setConvertedInvoiceId(Long convertedInvoiceId) { this.convertedInvoiceId = convertedInvoiceId; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getCustomerNote() { return customerNote; }
    public void setCustomerNote(String customerNote) { this.customerNote = customerNote; }

    public String getTermsAndConditions() { return termsAndConditions; }
    public void setTermsAndConditions(String termsAndConditions) { this.termsAndConditions = termsAndConditions; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Boolean getRoundOff() { return roundOff; }
    public void setRoundOff(Boolean roundOff) { this.roundOff = roundOff; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(String invoiceDate) { this.invoiceDate = invoiceDate; }

    public static class Discount {
        private String type;      // "PERCENT" or "VALUE"
        private BigDecimal value; // percent or amount (as per type)

        public Discount() {}
        public Discount(String type, BigDecimal value) {
            this.type = type;
            this.value = value;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public BigDecimal getValue() { return value; }
        public void setValue(BigDecimal value) { this.value = value; }
    }
}
