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
@Table(name = "savings", indexes = {
    @Index(name = "idx_savings_firm_date_id", columnList = "firmId, savingDate DESC, id DESC"),
    @Index(name = "idx_savings_firm_goal", columnList = "firmId, goalId")
})
public class SavingRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 100)
    private String category; // e.g. Mutual Funds / SIP, Gold & Silver, Emergency Fund, Fixed Deposit / RD, Business Reserves, Cash Savings, Tax Provision, or custom

    private LocalDate savingDate;

    @Column(length = 50)
    private String paymentMode; // Bank Transfer, UPI, Cash, Auto-Debit, Card

    private Long goalId; // Optional link to a Goal entity

    @Column(length = 1000)
    private String notes;

    @Column(length = 255)
    private String tags;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (savingDate == null) {
            savingDate = LocalDate.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDate getSavingDate() { return savingDate; }
    public void setSavingDate(LocalDate savingDate) { this.savingDate = savingDate; }

    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public Long getGoalId() { return goalId; }
    public void setGoalId(Long goalId) { this.goalId = goalId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static SavingRecordBuilder builder() {
        return new SavingRecordBuilder();
    }

    public static class SavingRecordBuilder {
        private Long id;
        private Long firmId;
        private String title;
        private BigDecimal amount;
        private String category;
        private LocalDate savingDate;
        private String paymentMode;
        private Long goalId;
        private String notes;
        private String tags;
        private LocalDateTime createdAt;

        public SavingRecordBuilder id(Long id) { this.id = id; return this; }
        public SavingRecordBuilder firmId(Long firmId) { this.firmId = firmId; return this; }
        public SavingRecordBuilder title(String title) { this.title = title; return this; }
        public SavingRecordBuilder amount(BigDecimal amount) { this.amount = amount; return this; }
        public SavingRecordBuilder category(String category) { this.category = category; return this; }
        public SavingRecordBuilder savingDate(LocalDate savingDate) { this.savingDate = savingDate; return this; }
        public SavingRecordBuilder paymentMode(String paymentMode) { this.paymentMode = paymentMode; return this; }
        public SavingRecordBuilder goalId(Long goalId) { this.goalId = goalId; return this; }
        public SavingRecordBuilder notes(String notes) { this.notes = notes; return this; }
        public SavingRecordBuilder tags(String tags) { this.tags = tags; return this; }
        public SavingRecordBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public SavingRecord build() {
            SavingRecord s = new SavingRecord();
            s.id = this.id;
            s.firmId = this.firmId;
            s.title = this.title;
            s.amount = this.amount;
            s.category = this.category;
            s.savingDate = this.savingDate;
            s.paymentMode = this.paymentMode;
            s.goalId = this.goalId;
            s.notes = this.notes;
            s.tags = this.tags;
            s.createdAt = this.createdAt;
            return s;
        }
    }
}
