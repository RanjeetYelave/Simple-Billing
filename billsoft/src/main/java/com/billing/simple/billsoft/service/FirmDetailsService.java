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

    /** Always return the single stored firm row */
    public FirmDetails get() {
        return repo.findById(1L).orElseGet(() -> {
            FirmDetails f = new FirmDetails();
            f.setId(1L);
            return repo.save(f);
        });
    }

    public FirmDetails update(FirmDetails payload) {

        FirmDetails existing = get(); // load existing
        payload.setId(1L);

        // ----------------------------
        // LOGO HANDLING (FIXED)
        // ----------------------------
        if (payload.getLogoBase64() == null) {
            // explicit removal
            payload.setLogoBase64(null);

        } else {
            String incoming = payload.getLogoBase64().trim();

            if (incoming.isBlank()) {
                payload.setLogoBase64(null);
            } else {

                // MUST ALWAYS KEEP FULL DATA URL
                boolean isDataUrl = incoming.startsWith("data:image");

                // Reject insanely large logos ( >1.5MB as base64 size)
                if (incoming.length() > 2_000_000) {
                    // keep old logo instead
                    payload.setLogoBase64(existing.getLogoBase64());
                } else {
                    // good → save AS-IS
                    payload.setLogoBase64(incoming);
                }
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

        return repo.save(payload);
    }

    private String nullIfBlank(String s) {
        if (s == null) return null;
        return s.trim().isEmpty() ? null : s.trim();
    }
}
