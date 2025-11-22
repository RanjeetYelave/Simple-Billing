package com.billing.simple.billsoft.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String invoiceNumber;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    // === Totals Calculated by Backend ===
    private Double subtotalWithoutTax;   // NEW
    private Double totalTax;             // NEW
    private Double totalDiscount;        // NEW
    private Double totalAmount;          // already exists but moved into this category

    // === Invoice-level discount ===
    private String invoiceDiscountType;  // NEW
    private Double invoiceDiscountValue; // NEW

    private LocalDateTime invoiceDate;

    private String notes;

    // === New Paid Flag ===
    @Builder.Default
    private Boolean paid = false;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<InvoiceItem> items = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.invoiceDate = LocalDateTime.now();
        if (paid == null) paid = false;
    }
}
