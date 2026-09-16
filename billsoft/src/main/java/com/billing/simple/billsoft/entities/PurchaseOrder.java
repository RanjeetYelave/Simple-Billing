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
@Table(name = "purchase_orders", indexes = {
    @Index(name = "idx_po_firm_party_status", columnList = "firmId, party_id, status"),
    @Index(name = "idx_po_firm_date_id", columnList = "firmId, poDate DESC, id DESC")
})
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

    @Builder.Default
    @Column(nullable = false)
    private Boolean hidePricesOnPo = false;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public LocalDate getPoDate() { return poDate; }
    public void setPoDate(LocalDate poDate) { this.poDate = poDate; }

    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(LocalDate expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }

    public Party getParty() { return party; }
    public void setParty(Party party) { this.party = party; }

    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty("partyId")
    public Long getPartyId() {
        return this.party != null ? this.party.getId() : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("partyId")
    public void setPartyId(Long partyId) {
        if (partyId != null) {
            if (this.party == null) {
                this.party = new Party();
            }
            this.party.setId(partyId);
        }
    }

    public String getPartyName() { return partyName; }
    public void setPartyName(String partyName) { this.partyName = partyName; }

    public String getPartyContactPerson() { return partyContactPerson; }
    public void setPartyContactPerson(String partyContactPerson) { this.partyContactPerson = partyContactPerson; }

    public String getPartyPhone() { return partyPhone; }
    public void setPartyPhone(String partyPhone) { this.partyPhone = partyPhone; }

    public String getPartyEmail() { return partyEmail; }
    public void setPartyEmail(String partyEmail) { this.partyEmail = partyEmail; }

    public String getPartyGstin() { return partyGstin; }
    public void setPartyGstin(String partyGstin) { this.partyGstin = partyGstin; }

    public String getPartyPan() { return partyPan; }
    public void setPartyPan(String partyPan) { this.partyPan = partyPan; }

    public String getPartyAddress() { return partyAddress; }
    public void setPartyAddress(String partyAddress) { this.partyAddress = partyAddress; }

    public PurchaseOrderStatus getStatus() { return status; }
    public void setStatus(PurchaseOrderStatus status) { this.status = status; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

    public Boolean getHidePricesOnPo() { return hidePricesOnPo != null ? hidePricesOnPo : false; }
    public void setHidePricesOnPo(Boolean hidePricesOnPo) { this.hidePricesOnPo = hidePricesOnPo != null ? hidePricesOnPo : false; }

    public List<PurchaseOrderItem> getItems() { return items; }
    public void setItems(List<PurchaseOrderItem> items) { this.items = items; }

    public BigDecimal getSubtotalWithoutTax() { return subtotalWithoutTax; }
    public void setSubtotalWithoutTax(BigDecimal subtotalWithoutTax) { this.subtotalWithoutTax = subtotalWithoutTax; }

    public BigDecimal getTotalGstAmount() { return totalGstAmount; }
    public void setTotalGstAmount(BigDecimal totalGstAmount) { this.totalGstAmount = totalGstAmount; }

    public BigDecimal getTotalDiscountAmount() { return totalDiscountAmount; }
    public void setTotalDiscountAmount(BigDecimal totalDiscountAmount) { this.totalDiscountAmount = totalDiscountAmount; }

    public BigDecimal getRoundOff() { return roundOff; }
    public void setRoundOff(BigDecimal roundOff) { this.roundOff = roundOff; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getTermsAndConditions() { return termsAndConditions; }
    public void setTermsAndConditions(String termsAndConditions) { this.termsAndConditions = termsAndConditions; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static PurchaseOrderBuilder builder() {
        return new PurchaseOrderBuilder();
    }

    public static class PurchaseOrderBuilder {
        private Long id;
        private Long firmId;
        private String poNumber;
        private LocalDate poDate;
        private LocalDate expectedDeliveryDate;
        private Party party;
        private String partyName;
        private String partyContactPerson;
        private String partyPhone;
        private String partyEmail;
        private String partyGstin;
        private String partyPan;
        private String partyAddress;
        private PurchaseOrderStatus status = PurchaseOrderStatus.ISSUED;
        private String paymentStatus = "YET_TO_PAY";
        private BigDecimal paidAmount = BigDecimal.ZERO;
        private String paymentMethod = "Bank Transfer";
        private String paymentTerms;
        private String referenceNumber;
        private String shippingAddress;
        private Boolean hidePricesOnPo = false;
        private List<PurchaseOrderItem> items = new ArrayList<>();
        private BigDecimal subtotalWithoutTax = BigDecimal.ZERO;
        private BigDecimal totalGstAmount = BigDecimal.ZERO;
        private BigDecimal totalDiscountAmount = BigDecimal.ZERO;
        private BigDecimal roundOff = BigDecimal.ZERO;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private String notes;
        private String termsAndConditions;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public PurchaseOrderBuilder id(Long id) { this.id = id; return this; }
        public PurchaseOrderBuilder firmId(Long firmId) { this.firmId = firmId; return this; }
        public PurchaseOrderBuilder poNumber(String poNumber) { this.poNumber = poNumber; return this; }
        public PurchaseOrderBuilder poDate(LocalDate poDate) { this.poDate = poDate; return this; }
        public PurchaseOrderBuilder expectedDeliveryDate(LocalDate expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; return this; }
        public PurchaseOrderBuilder party(Party party) { this.party = party; return this; }
        public PurchaseOrderBuilder partyName(String partyName) { this.partyName = partyName; return this; }
        public PurchaseOrderBuilder partyContactPerson(String partyContactPerson) { this.partyContactPerson = partyContactPerson; return this; }
        public PurchaseOrderBuilder partyPhone(String partyPhone) { this.partyPhone = partyPhone; return this; }
        public PurchaseOrderBuilder partyEmail(String partyEmail) { this.partyEmail = partyEmail; return this; }
        public PurchaseOrderBuilder partyGstin(String partyGstin) { this.partyGstin = partyGstin; return this; }
        public PurchaseOrderBuilder partyPan(String partyPan) { this.partyPan = partyPan; return this; }
        public PurchaseOrderBuilder partyAddress(String partyAddress) { this.partyAddress = partyAddress; return this; }
        public PurchaseOrderBuilder status(PurchaseOrderStatus status) { this.status = status; return this; }
        public PurchaseOrderBuilder paymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; return this; }
        public PurchaseOrderBuilder paidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; return this; }
        public PurchaseOrderBuilder paymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; return this; }
        public PurchaseOrderBuilder paymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; return this; }
        public PurchaseOrderBuilder referenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; return this; }
        public PurchaseOrderBuilder shippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; return this; }
        public PurchaseOrderBuilder hidePricesOnPo(Boolean hidePricesOnPo) { this.hidePricesOnPo = hidePricesOnPo != null ? hidePricesOnPo : false; return this; }
        public PurchaseOrderBuilder items(List<PurchaseOrderItem> items) { this.items = items; return this; }
        public PurchaseOrderBuilder subtotalWithoutTax(BigDecimal subtotalWithoutTax) { this.subtotalWithoutTax = subtotalWithoutTax; return this; }
        public PurchaseOrderBuilder totalGstAmount(BigDecimal totalGstAmount) { this.totalGstAmount = totalGstAmount; return this; }
        public PurchaseOrderBuilder totalDiscountAmount(BigDecimal totalDiscountAmount) { this.totalDiscountAmount = totalDiscountAmount; return this; }
        public PurchaseOrderBuilder roundOff(BigDecimal roundOff) { this.roundOff = roundOff; return this; }
        public PurchaseOrderBuilder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }
        public PurchaseOrderBuilder notes(String notes) { this.notes = notes; return this; }
        public PurchaseOrderBuilder termsAndConditions(String termsAndConditions) { this.termsAndConditions = termsAndConditions; return this; }
        public PurchaseOrderBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PurchaseOrderBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public PurchaseOrder build() {
            PurchaseOrder po = new PurchaseOrder();
            po.id = this.id;
            po.firmId = this.firmId;
            po.poNumber = this.poNumber;
            po.poDate = this.poDate;
            po.expectedDeliveryDate = this.expectedDeliveryDate;
            po.party = this.party;
            po.partyName = this.partyName;
            po.partyContactPerson = this.partyContactPerson;
            po.partyPhone = this.partyPhone;
            po.partyEmail = this.partyEmail;
            po.partyGstin = this.partyGstin;
            po.partyPan = this.partyPan;
            po.partyAddress = this.partyAddress;
            po.status = this.status != null ? this.status : PurchaseOrderStatus.ISSUED;
            po.paymentStatus = this.paymentStatus != null ? this.paymentStatus : "YET_TO_PAY";
            po.paidAmount = this.paidAmount != null ? this.paidAmount : BigDecimal.ZERO;
            po.paymentMethod = this.paymentMethod != null ? this.paymentMethod : "Bank Transfer";
            po.paymentTerms = this.paymentTerms;
            po.referenceNumber = this.referenceNumber;
            po.shippingAddress = this.shippingAddress;
            po.hidePricesOnPo = this.hidePricesOnPo != null ? this.hidePricesOnPo : false;
            po.items = this.items != null ? this.items : new ArrayList<>();
            po.subtotalWithoutTax = this.subtotalWithoutTax != null ? this.subtotalWithoutTax : BigDecimal.ZERO;
            po.totalGstAmount = this.totalGstAmount != null ? this.totalGstAmount : BigDecimal.ZERO;
            po.totalDiscountAmount = this.totalDiscountAmount != null ? this.totalDiscountAmount : BigDecimal.ZERO;
            po.roundOff = this.roundOff != null ? this.roundOff : BigDecimal.ZERO;
            po.totalAmount = this.totalAmount != null ? this.totalAmount : BigDecimal.ZERO;
            po.notes = this.notes;
            po.termsAndConditions = this.termsAndConditions;
            po.createdAt = this.createdAt;
            po.updatedAt = this.updatedAt;
            return po;
        }
    }
}


