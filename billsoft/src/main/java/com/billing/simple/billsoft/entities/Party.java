package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a Party (Vendor / Supplier / Contractor) from whom the firm orders or purchases goods/services.
 * Completely distinct from Customer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "parties")
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long firmId;

    @Column(nullable = false)
    private String name;

    private String contactPerson;

    @Column(length = 20)
    private String phone;

    private String email;

    @Column(length = 500)
    private String address;

    private String city;

    private String state;

    @Column(length = 10)
    private String pincode;

    @Column(length = 20)
    private String gstin;

    @Column(length = 20)
    private String pan;

    private String bankName;

    private String bankAccount;

    private String bankIfsc;

    private String upiId;

    @Builder.Default
    @Column(precision = 15, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    // "PAYABLE" (we owe them / debt) or "ADVANCE" (we paid extra / credit)
    @Builder.Default
    @Column(length = 20)
    private String openingBalanceType = "PAYABLE";

    @Column(length = 1000)
    private String notes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.openingBalance == null) {
            this.openingBalance = BigDecimal.ZERO;
        }
        if (this.openingBalanceType == null || this.openingBalanceType.trim().isEmpty()) {
            this.openingBalanceType = "PAYABLE";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFirmId() { return firmId; }
    public void setFirmId(Long firmId) { this.firmId = firmId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public String getPan() { return pan; }
    public void setPan(String pan) { this.pan = pan; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }

    public String getBankIfsc() { return bankIfsc; }
    public void setBankIfsc(String bankIfsc) { this.bankIfsc = bankIfsc; }

    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }

    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }

    public String getOpeningBalanceType() { return openingBalanceType; }
    public void setOpeningBalanceType(String openingBalanceType) { this.openingBalanceType = openingBalanceType; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static PartyBuilder builder() {
        return new PartyBuilder();
    }

    public static class PartyBuilder {
        private Long id;
        private Long firmId;
        private String name;
        private String contactPerson;
        private String phone;
        private String email;
        private String address;
        private String city;
        private String state;
        private String pincode;
        private String gstin;
        private String pan;
        private String bankName;
        private String bankAccount;
        private String bankIfsc;
        private String upiId;
        private BigDecimal openingBalance = BigDecimal.ZERO;
        private String openingBalanceType = "PAYABLE";
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public PartyBuilder id(Long id) { this.id = id; return this; }
        public PartyBuilder firmId(Long firmId) { this.firmId = firmId; return this; }
        public PartyBuilder name(String name) { this.name = name; return this; }
        public PartyBuilder contactPerson(String contactPerson) { this.contactPerson = contactPerson; return this; }
        public PartyBuilder phone(String phone) { this.phone = phone; return this; }
        public PartyBuilder email(String email) { this.email = email; return this; }
        public PartyBuilder address(String address) { this.address = address; return this; }
        public PartyBuilder city(String city) { this.city = city; return this; }
        public PartyBuilder state(String state) { this.state = state; return this; }
        public PartyBuilder pincode(String pincode) { this.pincode = pincode; return this; }
        public PartyBuilder gstin(String gstin) { this.gstin = gstin; return this; }
        public PartyBuilder pan(String pan) { this.pan = pan; return this; }
        public PartyBuilder bankName(String bankName) { this.bankName = bankName; return this; }
        public PartyBuilder bankAccount(String bankAccount) { this.bankAccount = bankAccount; return this; }
        public PartyBuilder bankIfsc(String bankIfsc) { this.bankIfsc = bankIfsc; return this; }
        public PartyBuilder upiId(String upiId) { this.upiId = upiId; return this; }
        public PartyBuilder openingBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; return this; }
        public PartyBuilder openingBalanceType(String openingBalanceType) { this.openingBalanceType = openingBalanceType; return this; }
        public PartyBuilder notes(String notes) { this.notes = notes; return this; }
        public PartyBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public PartyBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }

        public Party build() {
            Party p = new Party();
            p.id = this.id;
            p.firmId = this.firmId;
            p.name = this.name;
            p.contactPerson = this.contactPerson;
            p.phone = this.phone;
            p.email = this.email;
            p.address = this.address;
            p.city = this.city;
            p.state = this.state;
            p.pincode = this.pincode;
            p.gstin = this.gstin;
            p.pan = this.pan;
            p.bankName = this.bankName;
            p.bankAccount = this.bankAccount;
            p.bankIfsc = this.bankIfsc;
            p.upiId = this.upiId;
            p.openingBalance = this.openingBalance;
            p.openingBalanceType = this.openingBalanceType;
            p.notes = this.notes;
            p.createdAt = this.createdAt;
            p.updatedAt = this.updatedAt;
            return p;
        }
    }
}


