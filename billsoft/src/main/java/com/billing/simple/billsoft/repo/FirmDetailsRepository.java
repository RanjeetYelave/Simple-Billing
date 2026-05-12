package com.billing.simple.billsoft.repo;
import org.springframework.data.jpa.repository.JpaRepository;

import com.billing.simple.billsoft.entities.FirmDetails;

import java.util.Optional;

public interface FirmDetailsRepository extends JpaRepository<FirmDetails, Long> {
    Optional<FirmDetails> findByFirmName(String firmName);
}
