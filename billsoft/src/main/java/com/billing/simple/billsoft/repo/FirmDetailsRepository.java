package com.billing.simple.billsoft.repo;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.billing.simple.billsoft.entities.FirmDetails;

public interface FirmDetailsRepository extends JpaRepository<FirmDetails, Long> {
	
	Optional<FirmDetails> findByLoginIdIgnoreCase(String loginId);

}



