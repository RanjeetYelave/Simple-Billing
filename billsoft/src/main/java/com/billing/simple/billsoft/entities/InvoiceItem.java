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
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RELATIONS
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    @JsonBackReference
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // ITEM FIELDS (calculated / stored)
    private Integer qty;                   // Quantity
    private String unit;                   // Unit (pcs, kg etc.)

    private Double pricePerUnit;           // price per unit used for calculations
    private Double amountWithoutTax;       // qty * pricePerUnit

    // Discount
    private String discountType;           // "PERCENT" or "VALUE"
    private Double discountValue;          // flat rupee discount
    private Double discountPercent;        // percent discount

    private Double taxableAmount;          // amountWithoutTax - discount

    // GST
    private Double gstPercent;
    private Double gstAmount;

    // Final total for this row
    private Double lineTotal;
}
