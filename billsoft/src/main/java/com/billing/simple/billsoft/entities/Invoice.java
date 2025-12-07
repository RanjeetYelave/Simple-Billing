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
@Table(name = "invoices")
public class Invoice {

    // ------------------------
    // MULTI-FIRM OWNER
    // ------------------------
    @Column(nullable = false)
    private Long firmId;

    // ------------------------
    // PRIMARY KEY
    // ------------------------
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ------------------------
    // INVOICE / ESTIMATE NUMBERS
    // ------------------------
    @Column(unique = true, nullable = true)
    private String invoiceNumber;

    @Column(unique = true, nullable = true)
    private String estimateNumber;

    // ------------------------
    // STATUS
    // ------------------------
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.FINAL;

    // ------------------------
    // RELATIONS
    // ------------------------
    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<InvoiceItem> items = new ArrayList<>();

    // ------------------------
    // AMOUNTS (BigDecimal)
    // ------------------------
    @Column(precision = 15, scale = 2)
    private BigDecimal subtotalWithoutTax;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalTax;

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
    // EXTRA FIELDS
    // ------------------------
    @Column(length = 2000)
    private String customerNote;

    @Column(length = 2000)
    private String termsAndConditions;

    private String paymentMethod;
    private String currency = "INR";

    @Column(precision = 15, scale = 2)
    private BigDecimal roundOff;

    private String tags;

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
        totalDiscount = safe(totalDiscount);
        totalAmount = safe(totalAmount);
        invoiceDiscountValue = safe(invoiceDiscountValue);
        roundOff = safe(roundOff);
    }

    private BigDecimal safe(BigDecimal val) {
        return val == null ? null : val.setScale(2, RoundingMode.HALF_UP);
    }
}
