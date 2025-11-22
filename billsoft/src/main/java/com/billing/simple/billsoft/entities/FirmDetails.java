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
    private Long id = 1L; // no @GeneratedValue

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

    private String footerNote;
}
