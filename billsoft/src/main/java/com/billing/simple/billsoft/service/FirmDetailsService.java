package com.billing.simple.billsoft.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.repo.FirmDetailsRepository;

@Service
public class FirmDetailsService {

    private final FirmDetailsRepository repo;

    public FirmDetailsService(FirmDetailsRepository repo) {
        this.repo = repo;
    }

    /** Return all firms */
    public List<FirmDetails> list() {
        return repo.findAll();
    }

    /** Get firm by ID */
    public FirmDetails get(Long id) {
        return repo.findById(id).orElse(null);
    }

    /** Get the first available firm (fallback) */
    public FirmDetails getFirst() {
        List<FirmDetails> all = repo.findAll();
        if (!all.isEmpty()) return all.get(0);
        FirmDetails f = new FirmDetails();
        f.setFirmName("My Firm");
        return repo.save(f);
    }

    public FirmDetails create() {
        FirmDetails f = new FirmDetails();
        f.setFirmName("New Firm");
        return repo.save(f);
    }

    public FirmDetails update(Long id, FirmDetails payload) {
        FirmDetails existing = repo.findById(id).orElseThrow(() -> new RuntimeException("Firm not found: " + id));
        payload.setId(id);

        // ----------------------------
        // LOGO HANDLING (FIXED)
        // ----------------------------
        if (payload.getLogoBase64() == null) {
            payload.setLogoBase64(null);
        } else {
            String incoming = payload.getLogoBase64().trim();
            if (incoming.isBlank()) {
                payload.setLogoBase64(null);
            } else {
                if (incoming.length() > 2_000_000) {
                    payload.setLogoBase64(existing.getLogoBase64());
                } else {
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

    public void delete(Long id) {
        repo.deleteById(id);
    }

    private String nullIfBlank(String s) {
        if (s == null) return null;
        return s.trim().isEmpty() ? null : s.trim();
    }
}