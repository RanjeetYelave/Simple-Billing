package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Sales Return (Credit Note) filed against an invoice.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sales_returns", indexes = {
    @Index(name = "idx_sales_return_firm_date_id", columnList = "firmId, returnDate DESC, id DESC"),
    @Index(name = "idx_sales_return_invoice_id", columnList = "invoice_id")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SalesReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false, length = 50)
    private String returnNumber; // e.g. "CN-0001" or "RET-0001"

    private LocalDate returnDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "items"})
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Customer customer;

    @Column(length = 500)
    private String reason; // e.g., "Defective item", "Customer returned goods", "Excess billing"

    @Column(length = 50)
    private String refundMode; // "CASH", "BANK_TRANSFER", "UPI", "CUSTOMER_CREDIT"

    @Column(precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 15, scale = 2)
    private BigDecimal taxAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal excludedTaxAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal penaltyAmount;

    @Column(length = 255)
    private String penaltyReason;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalRefundAmount;

    @Column(length = 1000)
    private String notes;

    @OneToMany(mappedBy = "salesReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<SalesReturnItem> items = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.returnDate == null) {
            this.returnDate = LocalDate.now();
        }
        if (this.subtotal == null) this.subtotal = BigDecimal.ZERO;
        if (this.taxAmount == null) this.taxAmount = BigDecimal.ZERO;
        if (this.excludedTaxAmount == null) this.excludedTaxAmount = BigDecimal.ZERO;
        if (this.penaltyAmount == null) this.penaltyAmount = BigDecimal.ZERO;
        if (this.totalRefundAmount == null) this.totalRefundAmount = BigDecimal.ZERO;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public String getReturnNumber() { return returnNumber; }
    public void setReturnNumber(String returnNumber) { this.returnNumber = returnNumber; }

    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }

    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRefundMode() { return refundMode; }
    public void setRefundMode(String refundMode) { this.refundMode = refundMode; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getExcludedTaxAmount() { return excludedTaxAmount; }
    public void setExcludedTaxAmount(BigDecimal excludedTaxAmount) { this.excludedTaxAmount = excludedTaxAmount; }

    public BigDecimal getPenaltyAmount() { return penaltyAmount; }
    public void setPenaltyAmount(BigDecimal penaltyAmount) { this.penaltyAmount = penaltyAmount; }

    public String getPenaltyReason() { return penaltyReason; }
    public void setPenaltyReason(String penaltyReason) { this.penaltyReason = penaltyReason; }

    public BigDecimal getTotalRefundAmount() { return totalRefundAmount; }
    public void setTotalRefundAmount(BigDecimal totalRefundAmount) { this.totalRefundAmount = totalRefundAmount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<SalesReturnItem> getItems() { return items; }
    public void setItems(List<SalesReturnItem> items) { this.items = items; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static SalesReturnBuilder builder() {
        return new SalesReturnBuilder();
    }

    public static class SalesReturnBuilder {
        private Long id;
        private Long firmId;
        private String returnNumber;
        private LocalDate returnDate;
        private Invoice invoice;
        private Customer customer;
        private String reason;
        private String refundMode;
        private BigDecimal subtotal;
        private BigDecimal taxAmount;
        private BigDecimal excludedTaxAmount;
        private BigDecimal penaltyAmount;
        private String penaltyReason;
        private BigDecimal totalRefundAmount;
        private String notes;
        private List<SalesReturnItem> items = new ArrayList<>();
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public SalesReturnBuilder id(Long id) { this.id = id; return this; }
        public SalesReturnBuilder firmId(Long firmId) { this.firmId = firmId; return this; }
        public SalesReturnBuilder returnNumber(String returnNumber) { this.returnNumber = returnNumber; return this; }
        public SalesReturnBuilder returnDate(LocalDate returnDate) { this.returnDate = returnDate; return this; }
        public SalesReturnBuilder invoice(Invoice invoice) { this.invoice = invoice; return this; }
        public SalesReturnBuilder customer(Customer customer) { this.customer = customer; return this; }
        public SalesReturnBuilder reason(String reason) { this.reason = reason; return this; }
        public SalesReturnBuilder refundMode(String refundMode) { this.refundMode = refundMode; return this; }
        public SalesReturnBuilder subtotal(BigDecimal subtotal) { this.subtotal = subtotal; return this; }
        public SalesReturnBuilder taxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; return this; }
        public SalesReturnBuilder excludedTaxAmount(BigDecimal excludedTaxAmount) { this.excludedTaxAmount = excludedTaxAmount; return this; }
        public SalesReturnBuilder penaltyAmount(BigDecimal penaltyAmount) { this.penaltyAmount = penaltyAmount; return this; }
        public SalesReturnBuilder penaltyReason(String penaltyReason) { this.penaltyReason = penaltyReason; return this; }
        public SalesReturnBuilder totalRefundAmount(BigDecimal totalRefundAmount) { this.totalRefundAmount = totalRefundAmount; return this; }
        public SalesReturnBuilder notes(String notes) { this.notes = notes; return this; }
        public SalesReturnBuilder items(List<SalesReturnItem> items) { this.items = items; return this; }
        public SalesReturnBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public SalesReturnBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public SalesReturn build() {
            SalesReturn sr = new SalesReturn();
            sr.id = this.id;
            sr.firmId = this.firmId;
            sr.returnNumber = this.returnNumber;
            sr.returnDate = this.returnDate;
            sr.invoice = this.invoice;
            sr.customer = this.customer;
            sr.reason = this.reason;
            sr.refundMode = this.refundMode;
            sr.subtotal = this.subtotal;
            sr.taxAmount = this.taxAmount;
            sr.excludedTaxAmount = this.excludedTaxAmount;
            sr.penaltyAmount = this.penaltyAmount;
            sr.penaltyReason = this.penaltyReason;
            sr.totalRefundAmount = this.totalRefundAmount;
            sr.notes = this.notes;
            sr.items = this.items != null ? this.items : new ArrayList<>();
            sr.createdAt = this.createdAt;
            sr.updatedAt = this.updatedAt;
            return sr;
        }
    }
}


