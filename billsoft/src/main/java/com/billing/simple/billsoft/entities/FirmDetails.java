package com.billing.simple.billsoft.entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
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

    /**
     * For now we still assume a single row (id=1) in the DB.
     * Multi-firm support can later move this to @GeneratedValue and
     * adjust other services accordingly.
     */
    @Id
    private Long id = 1L;

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
     * Chosen by user – used to log in.
     * No strict format: min length & uniqueness handled in AuthService.
     */
    private String loginId;

    /**
     * SHA-256 hash of password with internal salt.
     * Never store raw passwords.
     */
    private String passwordHash;

    /**
     * Counter for failed login attempts for lockout logic.
     */
    private Integer failedLoginAttempts;

    /**
     * If not null and now < lockoutUntil → login is blocked.
     */
    private LocalDateTime lockoutUntil;

    // ---------------- LICENSING ----------------
    /**
     * "TRIAL", "PREMIUM", "PREMIUM_TEST" etc.
     */
    private String licenseLevel;

    /**
     * Encrypted expiry instant (epoch millis) for trial / premium.
     * AES-encrypted Base64 – so user cannot easily tamper just by editing DB.
     * If null and licenseLevel is null → no license / no trial.
     */
    private String licenseExpiryEncrypted;

    /**
     * When trial started (for info / analytics).
     */
    private LocalDate trialStartDate;

    /**
     * Total usage seconds (for future “loyalty discount” logic).
     * Not yet updated anywhere except via helper methods; safe to keep null.
     */
    private Long totalUsageSeconds;
}
