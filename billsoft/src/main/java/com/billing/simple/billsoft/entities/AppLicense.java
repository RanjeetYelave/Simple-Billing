package com.billing.simple.billsoft.entities;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "app_license")
public class AppLicense {

    @Id
    private Long id = 1L;   // always 1 row

    /**
     * "TRIAL", "PREMIUM", "PREMIUM_TEST"
     */
    private String licenseLevel;

    /**
     * Encrypted epoch millis (Instant) of expiry.
     */
    private String licenseExpiryEncrypted;

    /**
     * When the trial started.
     */
    private LocalDate trialStartDate;
}
