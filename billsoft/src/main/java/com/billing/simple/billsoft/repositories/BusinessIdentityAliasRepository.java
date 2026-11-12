package com.billing.simple.billsoft.repositories;

import com.billing.simple.billsoft.entities.BusinessIdentityAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessIdentityAliasRepository extends JpaRepository<BusinessIdentityAlias, Long> {

    List<BusinessIdentityAlias> findByFirmId(Long firmId);

    List<BusinessIdentityAlias> findByFirmIdAndLinkIdAndActiveTrue(Long firmId, Long linkId);

    List<BusinessIdentityAlias> findByFirmIdAndCustomerIdAndActiveTrue(Long firmId, Long customerId);

    List<BusinessIdentityAlias> findByFirmIdAndPartyIdAndActiveTrue(Long firmId, Long partyId);

    List<BusinessIdentityAlias> findByFirmIdAndNormalizedValueAndActiveTrue(Long firmId, String normalizedValue);

    Optional<BusinessIdentityAlias> findByFirmIdAndLinkIdAndNormalizedValueAndActiveTrue(Long firmId, Long linkId, String normalizedValue);

    boolean existsByFirmIdAndNormalizedValueAndActiveTrue(Long firmId, String normalizedValue);

    @Query("SELECT a FROM BusinessIdentityAlias a WHERE a.firmId = :firmId AND a.active = true AND LOWER(a.aliasValue) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<BusinessIdentityAlias> searchActiveAliases(@Param("firmId") Long firmId, @Param("query") String query);
}

