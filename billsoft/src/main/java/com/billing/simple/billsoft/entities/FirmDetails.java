package com.billing.simple.billsoft.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "firm_details")
public class FirmDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    private String firmName;
    private String ownerName;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String pincode;
    private String phone;
    private String email;
    private String gstin;

    @Lob
    private String logoBase64;

    private String bankName;
    private String bankAccount;
    private String bankIfsc;
    private String upiId;

    private String footerNote;

    private String invoicePrintTheme;
    private String invoicePrintThemeColor;
    private String invoicePrintFormat;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFirmName() { return firmName; }
    public void setFirmName(String firmName) { this.firmName = firmName; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public String getLogoBase64() { return logoBase64; }
    public void setLogoBase64(String logoBase64) { this.logoBase64 = logoBase64; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }

    public String getBankIfsc() { return bankIfsc; }
    public void setBankIfsc(String bankIfsc) { this.bankIfsc = bankIfsc; }

    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }

    public String getFooterNote() { return footerNote; }
    public void setFooterNote(String footerNote) { this.footerNote = footerNote; }

    public String getInvoicePrintTheme() { return invoicePrintTheme; }
    public void setInvoicePrintTheme(String invoicePrintTheme) { this.invoicePrintTheme = invoicePrintTheme; }

    public String getInvoicePrintThemeColor() { return invoicePrintThemeColor; }
    public void setInvoicePrintThemeColor(String invoicePrintThemeColor) { this.invoicePrintThemeColor = invoicePrintThemeColor; }

    public String getInvoicePrintFormat() { return invoicePrintFormat; }
    public void setInvoicePrintFormat(String invoicePrintFormat) { this.invoicePrintFormat = invoicePrintFormat; }
}