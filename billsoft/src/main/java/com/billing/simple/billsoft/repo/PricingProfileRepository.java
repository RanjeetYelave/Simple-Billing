package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.PricingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PricingProfileRepository extends JpaRepository<PricingProfile, Long> {

    List<PricingProfile> findByFirmIdOrderByIdAsc(Long firmId);

    List<PricingProfile> findByFirmIdAndIsActiveTrueOrderByIdAsc(Long firmId);

    Optional<PricingProfile> findByIdAndFirmId(Long id, Long firmId);

    Optional<PricingProfile> findByFirmIdAndCode(Long firmId, String code);

    Optional<PricingProfile> findFirstByFirmIdAndIsDefaultTrue(Long firmId);

    boolean existsByFirmIdAndCode(Long firmId, String code);

    boolean existsByFirmIdAndCodeAndIdNot(Long firmId, String code, Long id);
}
