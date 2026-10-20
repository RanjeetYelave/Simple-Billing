package com.billing.simple.billsoft.entities;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "invoices", indexes = {
    @Index(name = "idx_invoices_firm_date_id", columnList = "firmId, invoiceDate DESC, id DESC"),
    @Index(name = "idx_invoices_firm_status", columnList = "firmId, status"),
    @Index(name = "idx_invoices_firm_customer", columnList = "firmId, customer_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_inv_firm_number", columnNames = {"firmId", "invoiceNumber"})
})
public class Invoice {

    // ------------------------
    // PRIMARY KEY
    // ------------------------
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long version;

    // ------------------------
    // INVOICE / ESTIMATE NUMBERS
    // ------------------------
    @Column(nullable = true)
    private String invoiceNumber;

    @Column(nullable = true)
    private String estimateNumber;

    // ------------------------
    // STATUS
    // ------------------------
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, columnDefinition = "VARCHAR(50)")
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    // ------------------------
    // RELATIONS
    // ------------------------
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();

    // ------------------------
    // AMOUNTS (BigDecimal)
    // ------------------------
    @Column(precision = 15, scale = 2)
    private BigDecimal subtotalWithoutTax;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalTax;

    // GST Decomposition (AUDIT-032)
    @Column(precision = 15, scale = 2)
    private BigDecimal cgstAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal sgstAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal igstAmount;

    @Column(length = 100)
    private String placeOfSupply;

    // Immutable Master Data Snapshots at Document Issuance (AUDIT-036)
    @Column(length = 255)
    private String firmNameSnapshot;

    @Column(length = 50)
    private String firmGstinSnapshot;

    @Column(length = 1000)
    private String firmAddressSnapshot;

    @Column(length = 50)
    private String firmPhoneSnapshot;

    @Column(length = 100)
    private String firmEmailSnapshot;

    @Column(length = 255)
    private String customerNameSnapshot;

    @Column(length = 50)
    private String customerGstinSnapshot;

    @Column(length = 1000)
    private String customerAddressSnapshot;

    @Column(length = 50)
    private String customerPhoneSnapshot;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalDiscount;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalAmount;

    private String invoiceDiscountType;

    @Column(precision = 15, scale = 2)
    private BigDecimal invoiceDiscountValue;

    // ------------------------
    // DATES
    // ------------------------
    private LocalDateTime invoiceDate;
    private LocalDate dueDate;

    // For ESTIMATE → INVOICE mapping
    private Long convertedInvoiceId;

    // ------------------------
    // FLAGS
    // ------------------------
    @Builder.Default
    private Boolean paid = false;

    // ------------------------
    // FIRM ISOLATION
    // ------------------------
    @Column(nullable = false)
    private Long firmId;

    // ------------------------
    // EXTRA FIELDS
    // ------------------------
    @Column(length = 2000)
    private String customerNote;

    @Column(length = 2000)
    private String termsAndConditions;

    private String paymentMethod;
    @Builder.Default
    private String currency = "INR";

    @Column(precision = 15, scale = 2)
    private BigDecimal roundOff;

    @Column(length = 500)
    private String tags;

    // ------------------------
    // PRICING MODEL CONTEXT
    // ------------------------
    @Column(name = "pricing_profile_id")
    private Long pricingProfileId;

    // ------------------------
    // AUDIT FIELDS
    // ------------------------
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ------------------------
    // LIFECYCLE HOOKS
    // ------------------------
    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if (status == null)
            status = InvoiceStatus.FINAL;

        // Only set invoiceDate for actual invoices
        if (invoiceDate == null && status != InvoiceStatus.ESTIMATE) {
            invoiceDate = LocalDateTime.now();
        }

        // Ensure estimate NEVER uses invoiceNumber
        if (status == InvoiceStatus.ESTIMATE) {
            invoiceNumber = null;
        }

        if (paid == null)
            paid = false;

        normalizeDecimals();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
        normalizeDecimals();
    }

    // ------------------------
    // NORMALIZER FOR MONEY FIELDS
    // ------------------------
    private void normalizeDecimals() {
        subtotalWithoutTax = safe(subtotalWithoutTax);
        totalTax = safe(totalTax);
        cgstAmount = safe(cgstAmount);
        sgstAmount = safe(sgstAmount);
        igstAmount = safe(igstAmount);
        totalDiscount = safe(totalDiscount);
        totalAmount = safe(totalAmount);
        invoiceDiscountValue = safe(invoiceDiscountValue);
        roundOff = safe(roundOff);
    }

    private BigDecimal safe(BigDecimal val) {
        return val == null ? null : val.setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public String getEstimateNumber() { return estimateNumber; }
    public void setEstimateNumber(String estimateNumber) { this.estimateNumber = estimateNumber; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public List<InvoiceItem> getItems() { return items; }
    public void setItems(List<InvoiceItem> items) { this.items = items; }
    public BigDecimal getSubtotalWithoutTax() { return subtotalWithoutTax; }
    public void setSubtotalWithoutTax(BigDecimal subtotalWithoutTax) { this.subtotalWithoutTax = subtotalWithoutTax; }
    public BigDecimal getTotalTax() { return totalTax; }
    public void setTotalTax(BigDecimal totalTax) { this.totalTax = totalTax; }
    public BigDecimal getTotalDiscount() { return totalDiscount; }
    public void setTotalDiscount(BigDecimal totalDiscount) { this.totalDiscount = totalDiscount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getInvoiceDiscountType() { return invoiceDiscountType; }
    public void setInvoiceDiscountType(String invoiceDiscountType) { this.invoiceDiscountType = invoiceDiscountType; }
    public BigDecimal getInvoiceDiscountValue() { return invoiceDiscountValue; }
    public void setInvoiceDiscountValue(BigDecimal invoiceDiscountValue) { this.invoiceDiscountValue = invoiceDiscountValue; }
    public LocalDateTime getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDateTime invoiceDate) { this.invoiceDate = invoiceDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Long getConvertedInvoiceId() { return convertedInvoiceId; }
    public void setConvertedInvoiceId(Long convertedInvoiceId) { this.convertedInvoiceId = convertedInvoiceId; }
    public Boolean getPaid() { return paid; }
    public void setPaid(Boolean paid) { this.paid = paid; }
    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }
    public String getCustomerNote() { return customerNote; }
    public void setCustomerNote(String customerNote) { this.customerNote = customerNote; }
    public String getTermsAndConditions() { return termsAndConditions; }
    public void setTermsAndConditions(String termsAndConditions) { this.termsAndConditions = termsAndConditions; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getRoundOff() { return roundOff; }
    public void setRoundOff(BigDecimal roundOff) { this.roundOff = roundOff; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PostLoad
    public void ensureVersionNotNull() {
        if (version == null) {
            version = 0L;
        }
    }
}
