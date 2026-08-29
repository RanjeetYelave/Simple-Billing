package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    // ITEM BASIC FIELDS
    private Integer qty;
    private String unit;
    private String hsnCode; // HSN or SAC code

    // -------------------------------
    // MONEY FIELDS – BigDecimal
    // -------------------------------
    @Column(precision = 15, scale = 2)
    private BigDecimal pricePerUnit;

    @Column(precision = 15, scale = 2)
    private BigDecimal amountWithoutTax;

    // DISCOUNT
    private String discountType;     // "PERCENT" or "VALUE"

    @Column(precision = 15, scale = 2)
    private BigDecimal discountValue;

    @Column(precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(precision = 15, scale = 2)
    private BigDecimal taxableAmount;

    // GST
    @Column(precision = 5, scale = 2)
    private BigDecimal gstPercent;

    @Column(precision = 15, scale = 2)
    private BigDecimal gstAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal lineTotal;


    // --------------------------------
    // NORMALIZATION (Always Scale = 2)
    // --------------------------------
    @PrePersist
    @PreUpdate
    private void normalize() {

        if (pricePerUnit != null)
            pricePerUnit = pricePerUnit.setScale(2, RoundingMode.HALF_UP);

        if (amountWithoutTax != null)
            amountWithoutTax = amountWithoutTax.setScale(2, RoundingMode.HALF_UP);

        if (discountValue != null)
            discountValue = discountValue.setScale(2, RoundingMode.HALF_UP);

        if (discountPercent != null)
            discountPercent = discountPercent.setScale(2, RoundingMode.HALF_UP);

        if (taxableAmount != null)
            taxableAmount = taxableAmount.setScale(2, RoundingMode.HALF_UP);

        if (gstPercent != null)
            gstPercent = gstPercent.setScale(2, RoundingMode.HALF_UP);

        if (gstAmount != null)
            gstAmount = gstAmount.setScale(2, RoundingMode.HALF_UP);

        if (lineTotal != null)
            lineTotal = lineTotal.setScale(2, RoundingMode.HALF_UP);
    }
}
