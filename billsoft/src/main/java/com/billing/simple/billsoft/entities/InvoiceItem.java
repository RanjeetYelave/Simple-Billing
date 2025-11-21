package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "invoice_items")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "invoice"})
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ------------------------------
    // RELATIONS
    // ------------------------------
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    @JsonBackReference
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // ------------------------------
    // ITEM FIELDS (UI CALCULATED)
    // ------------------------------
    private Integer qty;                   // Quantity
    private String unit;                   // Kg / Unit / Box / Piece etc.

    private Double pricePerUnit;           // Price per single unit
    private Double amountWithoutTax;       // qty * pricePerUnit

    // Discount
    private String discountType;           // "PERCENT" or "VALUE"
    private Double discountValue;          // Flat ₹ discount
    private Double discountPercent;        // % discount

    private Double taxableAmount;          // After discount, before tax

    // GST
    private Double gstPercent;             // GST %
    private Double gstAmount;              // Calculated GST value

    // Final total for this row
    private Double lineTotal;              // taxableAmount + gstAmount
}
