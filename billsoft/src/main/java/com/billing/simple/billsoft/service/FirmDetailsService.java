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
     * Load firm by ID (from session or UI localStorage).
     * If not found → create empty safe profile for that ID.
     */
    public FirmDetails get(Long firmId) {
        if (firmId == null || firmId <= 0) {
            // invalid -> do NOT silently create junk; just create a fresh row
            FirmDetails f = new FirmDetails();
            return repo.save(f);
        }
        return repo.findById(firmId).orElseGet(() -> {
            FirmDetails f = new FirmDetails();
            return repo.save(f);
        });
    }

    /**
     * Update firm profile fields, preserving auth/security.
     */
    public FirmDetails update(Long firmId, FirmDetails payload) {

        FirmDetails existing = get(firmId);
        payload.setId(existing.getId()); // enforce immutability of ID

        // ----------------------------
        // LOGO HANDLING
        // ----------------------------
        String incoming = payload.getLogoBase64();

        if (incoming == null || incoming.trim().isEmpty()) {
            payload.setLogoBase64(null);
        } else {
            incoming = incoming.trim();
            if (!incoming.startsWith("data:image")) {
                incoming = "data:image/jpeg;base64," + incoming;
            }
            if (incoming.length() > 2_000_000) {
                payload.setLogoBase64(existing.getLogoBase64()); // keep old
            } else {
                payload.setLogoBase64(incoming);
            }
        }

        // ----------------------------
        // TEXT FIELDS (clean)
        // ----------------------------
        payload.setFirmName(clean(payload.getFirmName()));
        payload.setOwnerName(clean(payload.getOwnerName()));
        payload.setAddressLine1(clean(payload.getAddressLine1()));
        payload.setAddressLine2(clean(payload.getAddressLine2()));
        payload.setCity(clean(payload.getCity()));
        payload.setState(clean(payload.getState()));
        payload.setPincode(clean(payload.getPincode()));
        payload.setPhone(clean(payload.getPhone()));
        payload.setEmail(clean(payload.getEmail()));
        payload.setGstin(clean(payload.getGstin()));
        payload.setBankName(clean(payload.getBankName()));
        payload.setBankAccount(clean(payload.getBankAccount()));
        payload.setBankIfsc(clean(payload.getBankIfsc()));
        payload.setFooterNote(clean(payload.getFooterNote()));

        // ----------------------------
        // PRESERVE AUTH SECURITY
        // ----------------------------
        payload.setLoginId(existing.getLoginId());
        payload.setPasswordHash(existing.getPasswordHash());
        payload.setFailedLoginAttempts(existing.getFailedLoginAttempts());
        payload.setLockoutUntil(existing.getLockoutUntil());

        payload.setResetFailCount(existing.getResetFailCount());
        payload.setResetLockedUntil(existing.getResetLockedUntil());

        return repo.save(payload);
    }

    private String clean(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }
}
