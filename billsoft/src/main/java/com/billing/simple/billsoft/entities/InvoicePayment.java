package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "invoice_payments")
public class InvoicePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private Long invoiceId;

    private Long customerId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate paymentDate;

    @Column(length = 50)
    private String paymentMode; // Cash, UPI, Bank Transfer, Card, Cheque

    @Column(length = 100)
    private String referenceNumber;

    @Column(length = 1000)
    private String notes;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (paymentDate == null) {
            paymentDate = LocalDate.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static InvoicePaymentBuilder builder() {
        return new InvoicePaymentBuilder();
    }

    public static class InvoicePaymentBuilder {
        private Long id;
        private Long firmId;
        private Long invoiceId;
        private Long customerId;
        private BigDecimal amount;
        private LocalDate paymentDate;
        private String paymentMode;
        private String referenceNumber;
        private String notes;
        private LocalDateTime createdAt;

        public InvoicePaymentBuilder id(Long id) { this.id = id; return this; }
        public InvoicePaymentBuilder firmId(Long firmId) { this.firmId = firmId; return this; }
        public InvoicePaymentBuilder invoiceId(Long invoiceId) { this.invoiceId = invoiceId; return this; }
        public InvoicePaymentBuilder customerId(Long customerId) { this.customerId = customerId; return this; }
        public InvoicePaymentBuilder amount(BigDecimal amount) { this.amount = amount; return this; }
        public InvoicePaymentBuilder paymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; return this; }
        public InvoicePaymentBuilder paymentMode(String paymentMode) { this.paymentMode = paymentMode; return this; }
        public InvoicePaymentBuilder referenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; return this; }
        public InvoicePaymentBuilder notes(String notes) { this.notes = notes; return this; }
        public InvoicePaymentBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public InvoicePayment build() {
            InvoicePayment p = new InvoicePayment();
            p.id = this.id;
            p.firmId = this.firmId;
            p.invoiceId = this.invoiceId;
            p.customerId = this.customerId;
            p.amount = this.amount;
            p.paymentDate = this.paymentDate;
            p.paymentMode = this.paymentMode;
            p.referenceNumber = this.referenceNumber;
            p.notes = this.notes;
            p.createdAt = this.createdAt;
            return p;
        }
    }
}

