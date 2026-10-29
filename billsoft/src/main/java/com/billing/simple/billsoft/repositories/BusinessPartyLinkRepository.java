package com.billing.simple.billsoft.repositories;

import com.billing.simple.billsoft.entities.BusinessPartyLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessPartyLinkRepository extends JpaRepository<BusinessPartyLink, Long> {

    Optional<BusinessPartyLink> findByFirmIdAndCustomerId(Long firmId, Long customerId);

    Optional<BusinessPartyLink> findByFirmIdAndPartyId(Long firmId, Long partyId);

    Optional<BusinessPartyLink> findByFirmIdAndCustomerIdAndPartyId(Long firmId, Long customerId, Long partyId);

    List<BusinessPartyLink> findByFirmId(Long firmId);

    boolean existsByFirmIdAndCustomerId(Long firmId, Long customerId);

    boolean existsByFirmIdAndPartyId(Long firmId, Long partyId);

    boolean existsByCustomerId(Long customerId);

    boolean existsByPartyId(Long partyId);

    void deleteByFirmIdAndCustomerIdAndPartyId(Long firmId, Long customerId, Long partyId);
}
