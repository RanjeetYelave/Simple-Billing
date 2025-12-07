package com.billing.simple.billsoft.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.FirmDetails;

@Repository
public interface FirmDetailsRepository extends JpaRepository<FirmDetails, Long> {

    Optional<FirmDetails> findByLoginIdIgnoreCase(String loginId);
}
