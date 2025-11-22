package com.billing.simple.billsoft.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class FirmDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    private String bankName;
    private String bankAccountNo;
    private String bankIFSC;
    private String footerNote;

    // Optional logo stored as Base64 string
    private String logoBase64;

    // Invoice prefix : e.g. "INV-"
    private String invoicePrefix;
}
