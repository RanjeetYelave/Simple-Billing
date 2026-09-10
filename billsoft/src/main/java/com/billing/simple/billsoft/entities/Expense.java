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
@Table(name = "expenses", indexes = {
    @Index(name = "idx_expenses_firm_date_id", columnList = "firmId, expenseDate DESC, id DESC")
})
public class Expense {
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
    private String category; // e.g. Office Supplies, Rent & Facilities, Utilities, Salaries & Wages, Travel, Software & Tools, Marketing, Miscellaneous

    private LocalDate expenseDate;

    @Column(length = 50)
    private String paymentMode; // Cash, UPI, Bank Transfer, Card

    @Column(length = 1000)
    private String notes;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (expenseDate == null) {
            expenseDate = LocalDate.now();
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

    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }

    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static ExpenseBuilder builder() {
        return new ExpenseBuilder();
    }

    public static class ExpenseBuilder {
        private Long id;
        private Long firmId;
        private String title;
        private BigDecimal amount;
        private String category;
        private LocalDate expenseDate;
        private String paymentMode;
        private String notes;
        private LocalDateTime createdAt;

        public ExpenseBuilder id(Long id) { this.id = id; return this; }
        public ExpenseBuilder firmId(Long firmId) { this.firmId = firmId; return this; }
        public ExpenseBuilder title(String title) { this.title = title; return this; }
        public ExpenseBuilder amount(BigDecimal amount) { this.amount = amount; return this; }
        public ExpenseBuilder category(String category) { this.category = category; return this; }
        public ExpenseBuilder expenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; return this; }
        public ExpenseBuilder paymentMode(String paymentMode) { this.paymentMode = paymentMode; return this; }
        public ExpenseBuilder notes(String notes) { this.notes = notes; return this; }
        public ExpenseBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public Expense build() {
            Expense e = new Expense();
            e.id = this.id;
            e.firmId = this.firmId;
            e.title = this.title;
            e.amount = this.amount;
            e.category = this.category;
            e.expenseDate = this.expenseDate;
            e.paymentMode = this.paymentMode;
            e.notes = this.notes;
            e.createdAt = this.createdAt;
            return e;
        }
    }
}


