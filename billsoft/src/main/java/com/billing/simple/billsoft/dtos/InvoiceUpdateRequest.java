package com.billing.simple.billsoft.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.billing.simple.billsoft.entities.InvoiceStatus;

public class InvoiceUpdateRequest {

    private Long customerId;
    private String invoiceDate;
    private String notes;
    private InvoiceRequest.Discount invoiceDiscount;
    private Boolean paid;
    private List<InvoiceRequestItem> items;
    private InvoiceStatus status;
    private String estimateNumber;
    private Long convertedInvoiceId;
    private LocalDate dueDate;
    private String customerNote;
    private String termsAndConditions;
    private String paymentMethod;
    private String currency;
    private Boolean roundOff;
    private String tags;
    private Boolean allowOutOfStock;
    private String customerName;
    private String customerPhone;
    private String customerAddress;
    private String customerEmail;
    private String customerGstin;
    private Boolean updateCanonicalCustomer;
    private Boolean clearCustomer;
    private String idempotencyKey;

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getCustomerAddress() { return customerAddress; }
    public void setCustomerAddress(String customerAddress) { this.customerAddress = customerAddress; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getCustomerGstin() { return customerGstin; }
    public void setCustomerGstin(String customerGstin) { this.customerGstin = customerGstin; }

    public Boolean getUpdateCanonicalCustomer() { return updateCanonicalCustomer; }
    public void setUpdateCanonicalCustomer(Boolean updateCanonicalCustomer) { this.updateCanonicalCustomer = updateCanonicalCustomer; }

    public Boolean getClearCustomer() { return clearCustomer; }
    public void setClearCustomer(Boolean clearCustomer) { this.clearCustomer = clearCustomer; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Boolean getAllowOutOfStock() { return allowOutOfStock; }
    public void setAllowOutOfStock(Boolean allowOutOfStock) { this.allowOutOfStock = allowOutOfStock; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(String invoiceDate) { this.invoiceDate = invoiceDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public InvoiceRequest.Discount getInvoiceDiscount() { return invoiceDiscount; }
    public void setInvoiceDiscount(InvoiceRequest.Discount invoiceDiscount) { this.invoiceDiscount = invoiceDiscount; }

    public Boolean getPaid() { return paid; }
    public void setPaid(Boolean paid) { this.paid = paid; }

    public List<InvoiceRequestItem> getItems() { return items; }
    public void setItems(List<InvoiceRequestItem> items) { this.items = items; }

    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }

    public String getEstimateNumber() { return estimateNumber; }
    public void setEstimateNumber(String estimateNumber) { this.estimateNumber = estimateNumber; }

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
}
