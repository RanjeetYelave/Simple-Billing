package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a payment transaction made to a Party (Vendor / Supplier).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "party_payments", indexes = {
    @Index(name = "idx_payments_firm_party", columnList = "firmId, partyId, paymentDate DESC")
})
public class PartyPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long partyId;

    @Column(nullable = false)
    private Long firmId;

    private Long purchaseOrderId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate paymentDate;

    // CASH, BANK_TRANSFER, UPI, CHEQUE, OTHER
    @Builder.Default
    @Column(length = 30)
    private String paymentMode = "BANK_TRANSFER";

    private String referenceNumber;

    @Column(length = 1000)
    private String notes;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.paymentDate == null) {
            this.paymentDate = LocalDate.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public Long getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }

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

    public static PartyPaymentBuilder builder() {
        return new PartyPaymentBuilder();
    }

    public static class PartyPaymentBuilder {
        private Long id;
        private Long partyId;
        private Long firmId;
        private Long purchaseOrderId;
        private BigDecimal amount;
        private LocalDate paymentDate;
        private String paymentMode = "BANK_TRANSFER";
        private String referenceNumber;
        private String notes;
        private LocalDateTime createdAt;

        public PartyPaymentBuilder id(Long id) { this.id = id; return this; }
        public PartyPaymentBuilder partyId(Long partyId) { this.partyId = partyId; return this; }
        public PartyPaymentBuilder firmId(Long firmId) { this.firmId = firmId; return this; }
        public PartyPaymentBuilder purchaseOrderId(Long purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; return this; }
        public PartyPaymentBuilder amount(BigDecimal amount) { this.amount = amount; return this; }
        public PartyPaymentBuilder paymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; return this; }
        public PartyPaymentBuilder paymentMode(String paymentMode) { this.paymentMode = paymentMode; return this; }
        public PartyPaymentBuilder referenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; return this; }
        public PartyPaymentBuilder notes(String notes) { this.notes = notes; return this; }
        public PartyPaymentBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public PartyPayment build() {
            PartyPayment p = new PartyPayment();
            p.id = this.id;
            p.partyId = this.partyId;
            p.firmId = this.firmId;
            p.purchaseOrderId = this.purchaseOrderId;
            p.amount = this.amount;
            p.paymentDate = this.paymentDate;
            p.paymentMode = this.paymentMode != null ? this.paymentMode : "BANK_TRANSFER";
            p.referenceNumber = this.referenceNumber;
            p.notes = this.notes;
            p.createdAt = this.createdAt;
            return p;
        }
    }
}


