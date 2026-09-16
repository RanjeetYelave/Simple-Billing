package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing an official letter drafted on firm letterhead or custom sender.
 * Can be sent FROM a selected Firm or Custom Sender, and TO a Party, Customer, or Custom recipient.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "business_letters", indexes = {
    @Index(name = "idx_letters_firm_date_id", columnList = "firmId, letterDate DESC, id DESC")
})
public class BusinessLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private String letterNumber; // e.g. LTR-2026-0001

    @Column(nullable = false)
    private LocalDate letterDate;

    // ── FROM (Sender) ──
    @Builder.Default
    @Column(length = 20)
    private String senderType = "FIRM"; // "FIRM" or "CUSTOM"

    private String senderName;

    private String senderCompany;

    @Column(length = 500)
    private String senderAddress;

    private String senderPhone;

    private String senderEmail;

    private String senderGstin;

    // ── TO (Recipient) ──
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private LetterRecipientType recipientType = LetterRecipientType.CUSTOM;

    private Long partyId;

    private Long customerId;

    @Column(nullable = false)
    private String recipientName;

    private String recipientDesignation;

    private String recipientCompany;

    @Column(length = 500)
    private String recipientAddress;

    private String recipientPhone;

    private String recipientEmail;

    // ── Letter Content ──
    @Column(nullable = false, length = 500)
    private String subject;

    @Builder.Default
    @Column(length = 50)
    private String category = "GENERAL"; // GENERAL, NOTICE, PAYMENT_REMINDER, APPRECIATION, AUTHORIZATION, AGREEMENT

    @Column(columnDefinition = "TEXT", length = 10000, nullable = false)
    private String content;

    private String signatoryName;

    private String signatoryDesignation;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private LetterStatus status = LetterStatus.ISSUED;

    @Builder.Default
    private Boolean includeHeader = true;

    @Builder.Default
    private Boolean includeFooter = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.letterDate == null) {
            this.letterDate = LocalDate.now();
        }
        if (this.senderType == null || this.senderType.trim().isEmpty()) {
            this.senderType = "FIRM";
        }
        if (this.status == null) {
            this.status = LetterStatus.ISSUED;
        }
        if (this.recipientType == null) {
            this.recipientType = LetterRecipientType.CUSTOM;
        }
        if (this.category == null || this.category.trim().isEmpty()) {
            this.category = "GENERAL";
        }
        if (this.includeHeader == null) {
            this.includeHeader = true;
        }
        if (this.includeFooter == null) {
            this.includeFooter = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public String getLetterNumber() { return letterNumber; }
    public void setLetterNumber(String letterNumber) { this.letterNumber = letterNumber; }

    public LocalDate getLetterDate() { return letterDate; }
    public void setLetterDate(LocalDate letterDate) { this.letterDate = letterDate; }

    public String getSenderType() { return senderType; }
    public void setSenderType(String senderType) { this.senderType = senderType; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderCompany() { return senderCompany; }
    public void setSenderCompany(String senderCompany) { this.senderCompany = senderCompany; }

    public String getSenderAddress() { return senderAddress; }
    public void setSenderAddress(String senderAddress) { this.senderAddress = senderAddress; }

    public String getSenderPhone() { return senderPhone; }
    public void setSenderPhone(String senderPhone) { this.senderPhone = senderPhone; }

    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }

    public String getSenderGstin() { return senderGstin; }
    public void setSenderGstin(String senderGstin) { this.senderGstin = senderGstin; }

    public LetterRecipientType getRecipientType() { return recipientType; }
    public void setRecipientType(LetterRecipientType recipientType) { this.recipientType = recipientType; }

    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getRecipientDesignation() { return recipientDesignation; }
    public void setRecipientDesignation(String recipientDesignation) { this.recipientDesignation = recipientDesignation; }

    public String getRecipientCompany() { return recipientCompany; }
    public void setRecipientCompany(String recipientCompany) { this.recipientCompany = recipientCompany; }

    public String getRecipientAddress() { return recipientAddress; }
    public void setRecipientAddress(String recipientAddress) { this.recipientAddress = recipientAddress; }

    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }

    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSignatoryName() { return signatoryName; }
    public void setSignatoryName(String signatoryName) { this.signatoryName = signatoryName; }

    public String getSignatoryDesignation() { return signatoryDesignation; }
    public void setSignatoryDesignation(String signatoryDesignation) { this.signatoryDesignation = signatoryDesignation; }

    public LetterStatus getStatus() { return status; }
    public void setStatus(LetterStatus status) { this.status = status; }

    public Boolean getIncludeHeader() { return includeHeader; }
    public void setIncludeHeader(Boolean includeHeader) { this.includeHeader = includeHeader; }

    public Boolean getIncludeFooter() { return includeFooter; }
    public void setIncludeFooter(Boolean includeFooter) { this.includeFooter = includeFooter; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static BusinessLetterBuilder builder() {
        return new BusinessLetterBuilder();
    }

    public static class BusinessLetterBuilder {
        private Long id;
        private Long firmId;
        private String letterNumber;
        private LocalDate letterDate;
        private String senderType = "FIRM";
        private String senderName;
        private String senderCompany;
        private String senderAddress;
        private String senderPhone;
        private String senderEmail;
        private String senderGstin;
        private LetterRecipientType recipientType = LetterRecipientType.CUSTOM;
        private Long partyId;
        private Long customerId;
        private String recipientName;
        private String recipientDesignation;
        private String recipientCompany;
        private String recipientAddress;
        private String recipientPhone;
        private String recipientEmail;
        private String subject;
        private String category = "GENERAL";
        private String content;
        private String signatoryName;
        private String signatoryDesignation;
        private LetterStatus status = LetterStatus.ISSUED;
        private Boolean includeHeader = true;
        private Boolean includeFooter = true;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public BusinessLetterBuilder id(Long id) { this.id = id; return this; }
        public BusinessLetterBuilder firmId(Long firmId) { this.firmId = firmId; return this; }
        public BusinessLetterBuilder letterNumber(String letterNumber) { this.letterNumber = letterNumber; return this; }
        public BusinessLetterBuilder letterDate(LocalDate letterDate) { this.letterDate = letterDate; return this; }
        public BusinessLetterBuilder senderType(String senderType) { this.senderType = senderType; return this; }
        public BusinessLetterBuilder senderName(String senderName) { this.senderName = senderName; return this; }
        public BusinessLetterBuilder senderCompany(String senderCompany) { this.senderCompany = senderCompany; return this; }
        public BusinessLetterBuilder senderAddress(String senderAddress) { this.senderAddress = senderAddress; return this; }
        public BusinessLetterBuilder senderPhone(String senderPhone) { this.senderPhone = senderPhone; return this; }
        public BusinessLetterBuilder senderEmail(String senderEmail) { this.senderEmail = senderEmail; return this; }
        public BusinessLetterBuilder senderGstin(String senderGstin) { this.senderGstin = senderGstin; return this; }
        public BusinessLetterBuilder recipientType(LetterRecipientType recipientType) { this.recipientType = recipientType; return this; }
        public BusinessLetterBuilder partyId(Long partyId) { this.partyId = partyId; return this; }
        public BusinessLetterBuilder customerId(Long customerId) { this.customerId = customerId; return this; }
        public BusinessLetterBuilder recipientName(String recipientName) { this.recipientName = recipientName; return this; }
        public BusinessLetterBuilder recipientDesignation(String recipientDesignation) { this.recipientDesignation = recipientDesignation; return this; }
        public BusinessLetterBuilder recipientCompany(String recipientCompany) { this.recipientCompany = recipientCompany; return this; }
        public BusinessLetterBuilder recipientAddress(String recipientAddress) { this.recipientAddress = recipientAddress; return this; }
        public BusinessLetterBuilder recipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; return this; }
        public BusinessLetterBuilder recipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; return this; }
        public BusinessLetterBuilder subject(String subject) { this.subject = subject; return this; }
        public BusinessLetterBuilder category(String category) { this.category = category; return this; }
        public BusinessLetterBuilder content(String content) { this.content = content; return this; }
        public BusinessLetterBuilder signatoryName(String signatoryName) { this.signatoryName = signatoryName; return this; }
        public BusinessLetterBuilder signatoryDesignation(String signatoryDesignation) { this.signatoryDesignation = signatoryDesignation; return this; }
        public BusinessLetterBuilder status(LetterStatus status) { this.status = status; return this; }
        public BusinessLetterBuilder includeHeader(Boolean includeHeader) { this.includeHeader = includeHeader; return this; }
        public BusinessLetterBuilder includeFooter(Boolean includeFooter) { this.includeFooter = includeFooter; return this; }
        public BusinessLetterBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public BusinessLetterBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public BusinessLetter build() {
            BusinessLetter l = new BusinessLetter();
            l.id = this.id;
            l.firmId = this.firmId;
            l.letterNumber = this.letterNumber;
            l.letterDate = this.letterDate;
            l.senderType = this.senderType != null ? this.senderType : "FIRM";
            l.senderName = this.senderName;
            l.senderCompany = this.senderCompany;
            l.senderAddress = this.senderAddress;
            l.senderPhone = this.senderPhone;
            l.senderEmail = this.senderEmail;
            l.senderGstin = this.senderGstin;
            l.recipientType = this.recipientType != null ? this.recipientType : LetterRecipientType.CUSTOM;
            l.partyId = this.partyId;
            l.customerId = this.customerId;
            l.recipientName = this.recipientName;
            l.recipientDesignation = this.recipientDesignation;
            l.recipientCompany = this.recipientCompany;
            l.recipientAddress = this.recipientAddress;
            l.recipientPhone = this.recipientPhone;
            l.recipientEmail = this.recipientEmail;
            l.subject = this.subject;
            l.category = this.category != null ? this.category : "GENERAL";
            l.content = this.content;
            l.signatoryName = this.signatoryName;
            l.signatoryDesignation = this.signatoryDesignation;
            l.status = this.status != null ? this.status : LetterStatus.ISSUED;
            l.includeHeader = this.includeHeader != null ? this.includeHeader : true;
            l.includeFooter = this.includeFooter != null ? this.includeFooter : true;
            l.createdAt = this.createdAt;
            l.updatedAt = this.updatedAt;
            return l;
        }
    }
}


