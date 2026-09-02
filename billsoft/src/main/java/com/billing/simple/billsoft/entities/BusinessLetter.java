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
@Table(name = "business_letters")
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
}
