package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Purchase Order entity representing an official order sent to a Party (Vendor / Supplier).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private String poNumber;

    @Column(nullable = false)
    private LocalDate poDate;

    private LocalDate expectedDeliveryDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    // Snapshot of party info at time of PO creation
    private String partyName;
    private String partyContactPerson;
    private String partyPhone;
    private String partyEmail;
    private String partyGstin;
    private String partyPan;

    @Column(length = 500)
    private String partyAddress;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 30)
    private PurchaseOrderStatus status = PurchaseOrderStatus.ISSUED;

    // Simple payment tracking: YET_TO_PAY, PAID, PARTIAL
    @Builder.Default
    @Column(length = 30)
    private String paymentStatus = "YET_TO_PAY";

    @Builder.Default
    @Column(precision = 15, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(length = 50)
    private String paymentMethod = "Bank Transfer";

    @Column(length = 100)
    private String paymentTerms;

    private String referenceNumber;

    @Column(length = 500)
    private String shippingAddress;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<PurchaseOrderItem> items = new ArrayList<>();

    @Builder.Default
    @Column(precision = 15, scale = 2)
    private BigDecimal subtotalWithoutTax = BigDecimal.ZERO;

    @Builder.Default
    @Column(precision = 15, scale = 2)
    private BigDecimal totalGstAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(precision = 15, scale = 2)
    private BigDecimal totalDiscountAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(precision = 15, scale = 2)
    private BigDecimal roundOff = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(length = 2000)
    private String notes;

    @Column(length = 2000)
    private String termsAndConditions;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.poDate == null) {
            this.poDate = LocalDate.now();
        }
        if (this.status == null) {
            this.status = PurchaseOrderStatus.ISSUED;
        }
        if (this.paymentStatus == null || this.paymentStatus.trim().isEmpty()) {
            this.paymentStatus = "YET_TO_PAY";
        }
        if (this.paidAmount == null) {
            this.paidAmount = BigDecimal.ZERO;
        }
        recalculateTotals();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        recalculateTotals();
    }

    /**
     * Recalculates subtotal, GST, discounts, roundoff, and grand total.
     */
    public void recalculateTotals() {
        if (items == null || items.isEmpty()) {
            this.subtotalWithoutTax = BigDecimal.ZERO;
            this.totalGstAmount = BigDecimal.ZERO;
            this.totalDiscountAmount = BigDecimal.ZERO;
            this.roundOff = BigDecimal.ZERO;
            this.totalAmount = BigDecimal.ZERO;
            return;
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalGst = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (PurchaseOrderItem item : items) {
            BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE;
            BigDecimal price = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal discount = item.getDiscountValue() != null ? item.getDiscountValue() : BigDecimal.ZERO;
            BigDecimal gstRate = item.getGstPercent() != null ? item.getGstPercent() : BigDecimal.ZERO;

            BigDecimal gross = qty.multiply(price);
            BigDecimal taxable = gross.subtract(discount).max(BigDecimal.ZERO);
            BigDecimal gst = taxable.multiply(gstRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = taxable.add(gst);

            item.setTaxableAmount(taxable);
            item.setGstAmount(gst);
            item.setTotalAmount(lineTotal);

            subtotal = subtotal.add(taxable);
            totalGst = totalGst.add(gst);
            totalDiscount = totalDiscount.add(discount);
        }

        this.subtotalWithoutTax = subtotal.setScale(2, RoundingMode.HALF_UP);
        this.totalGstAmount = totalGst.setScale(2, RoundingMode.HALF_UP);
        this.totalDiscountAmount = totalDiscount.setScale(2, RoundingMode.HALF_UP);

        BigDecimal rawTotal = subtotal.add(totalGst);
        BigDecimal roundedTotal = rawTotal.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
        this.roundOff = roundedTotal.subtract(rawTotal).setScale(2, RoundingMode.HALF_UP);
        this.totalAmount = roundedTotal;
    }
}
