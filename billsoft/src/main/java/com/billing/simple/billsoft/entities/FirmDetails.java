package com.billing.simple.billsoft.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "firm_details")
public class FirmDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;   // <-- auto-generated, each firm gets its own ID

    // ---------------- BASIC PROFILE ----------------
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

    // ---------------- AUTH / LOGIN ----------------
    /**
     * Login ID chosen by user.
     * Must be unique (enforced in AuthService).
     */
    private String loginId;

    /**
     * SHA-256 hash of password with internal salt.
     */
    private String passwordHash;

    /**
     * Failed login attempts for this firm login.
     */
    private Integer failedLoginAttempts;

    /**
     * If not null and now < lockoutUntil → login is blocked.
     */
    private LocalDateTime lockoutUntil;

    // ---------------- USAGE / FUTURE ----------------
    /**
     * For future reporting if you want per-firm usage tracking.
     */
    private Long totalUsageSeconds;

    // ---------------- SUPPORT RESET SECURITY ----------------
    private Integer resetFailCount;
    private LocalDateTime resetLockedUntil;
}
