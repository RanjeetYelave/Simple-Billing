package com.billing.simple.billsoft.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String invoiceNumber;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    // GRAND TOTAL (final invoice amount — after discounts & taxes)
    private Double totalAmount;

    // additional stored totals for visibility & auditing
    private Double subtotalWithoutTax; // sum of amountWithoutTax across items
    private Double totalTax; // sum of GST amounts across items
    private Double totalDiscount; // sum of item discounts + invoice-level discount (in rupees)

    // invoice level discount info (if applied)
    private String invoiceDiscountType; // "PERCENT" or "VALUE"
    private Double invoiceDiscountValue; // value or percent (store raw)

    private LocalDateTime invoiceDate;

    private String notes;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.invoiceDate == null) this.invoiceDate = LocalDateTime.now();
    }
}
