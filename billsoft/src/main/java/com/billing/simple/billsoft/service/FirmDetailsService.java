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

    public FirmDetails get() {
        return repo.findById(1L).orElseGet(() -> {
            FirmDetails f = new FirmDetails();
            f.setId(1L);
            return repo.save(f);
        });
    }

    public FirmDetails update(FirmDetails payload) {
        payload.setId(1L); // ALWAYS FORCE ID = 1
        return repo.save(payload);
    }
}
