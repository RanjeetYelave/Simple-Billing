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

    // Always return the single profile row (ID = 1)
    public FirmDetails get() {
        return repo.findById(1L).orElseGet(() -> {
            FirmDetails f = new FirmDetails();
            f.setId(1L); // works because entity now has setId()
            return repo.save(f);
        });
    }

    // Update only row with ID = 1
    public FirmDetails update(FirmDetails payload) {

        FirmDetails existing = get(); // load row 1

        // Copy fields from payload into existing record
        existing.setFirmName(payload.getFirmName());
        existing.setOwnerName(payload.getOwnerName());
        existing.setAddressLine1(payload.getAddressLine1());
        existing.setAddressLine2(payload.getAddressLine2());
        existing.setCity(payload.getCity());
        existing.setState(payload.getState());
        existing.setPincode(payload.getPincode());
        existing.setPhone(payload.getPhone());
        existing.setEmail(payload.getEmail());
        existing.setGstin(payload.getGstin());

        existing.setBankName(payload.getBankName());
        existing.setBankAccountNo(payload.getBankAccountNo());
        existing.setBankIFSC(payload.getBankIFSC());
        existing.setFooterNote(payload.getFooterNote());

        existing.setLogoBase64(payload.getLogoBase64());
        existing.setInvoicePrefix(payload.getInvoicePrefix());

        return repo.save(existing);
    }
}
