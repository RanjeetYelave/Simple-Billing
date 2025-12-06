package com.billing.simple.billsoft.service;

import org.springframework.stereotype.Service;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;

@Service
public class FirmDetailsService {

    private final FirmDetailsRepository repo;

    public FirmDetailsService(FirmDetailsRepository repo) {
        this.repo = repo;
    }

    /**
     * Always return the single firm row (id = 1).
     * If not present, create an empty one.
     */
    public FirmDetails get() {
        return repo.findById(1L).orElseGet(() -> {
            FirmDetails f = new FirmDetails();
            f.setId(1L);
            return repo.save(f);
        });
    }

    /**
     * Update firm profile fields + logo, while preserving
     * auth & license fields (loginId, passwordHash, lockout, license, usage).
     */
    public FirmDetails update(FirmDetails payload) {

        FirmDetails existing = get(); // load existing main row

        // Always keep the canonical ID = 1
        payload.setId(1L);

        // ----------------------------
        // LOGO HANDLING
        // ----------------------------
        String incoming = payload.getLogoBase64();

        if (incoming == null || incoming.trim().isEmpty()) {
            // explicit removal or empty -> wipe logo
            payload.setLogoBase64(null);

        } else {
            incoming = incoming.trim();

            // Normalize – ensure it always remains a data URL
            if (!incoming.startsWith("data:image")) {
                incoming = "data:image/jpeg;base64," + incoming;
            }

            // Reject too large logos (> 1.5MB text size)
            if (incoming.length() > 2_000_000) {
                // keep OLD logo instead of breaking existing one
                payload.setLogoBase64(existing.getLogoBase64());
            } else {
                // safe size -> store as-is
                payload.setLogoBase64(incoming);
            }
        }

        // ----------------------------
        // TEXT FIELDS
        // ----------------------------
        payload.setFirmName(nullIfBlank(payload.getFirmName()));
        payload.setOwnerName(nullIfBlank(payload.getOwnerName()));
        payload.setAddressLine1(nullIfBlank(payload.getAddressLine1()));
        payload.setAddressLine2(nullIfBlank(payload.getAddressLine2()));
        payload.setCity(nullIfBlank(payload.getCity()));
        payload.setState(nullIfBlank(payload.getState()));
        payload.setPincode(nullIfBlank(payload.getPincode()));
        payload.setPhone(nullIfBlank(payload.getPhone()));
        payload.setEmail(nullIfBlank(payload.getEmail()));
        payload.setGstin(nullIfBlank(payload.getGstin()));
        payload.setBankName(nullIfBlank(payload.getBankName()));
        payload.setBankAccount(nullIfBlank(payload.getBankAccount()));
        payload.setBankIfsc(nullIfBlank(payload.getBankIfsc()));
        payload.setFooterNote(nullIfBlank(payload.getFooterNote()));

        // ----------------------------
        // PRESERVE AUTH & LICENSE FIELDS
        // ----------------------------
        payload.setLoginId(existing.getLoginId());
        payload.setPasswordHash(existing.getPasswordHash());
        payload.setFailedLoginAttempts(existing.getFailedLoginAttempts());
        payload.setLockoutUntil(existing.getLockoutUntil());

        payload.setLicenseLevel(existing.getLicenseLevel());
        payload.setLicenseExpiryEncrypted(existing.getLicenseExpiryEncrypted());
        payload.setTrialStartDate(existing.getTrialStartDate());
        payload.setTotalUsageSeconds(existing.getTotalUsageSeconds());

        return repo.save(payload);
    }

    private String nullIfBlank(String s) {
        if (s == null) return null;
        return s.trim().isEmpty() ? null : s.trim();
    }
}
