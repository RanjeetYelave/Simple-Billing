package com.billing.simple.billsoft.repositories;

import com.billing.simple.billsoft.entities.BusinessPartyLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessPartyLinkRepository extends JpaRepository<BusinessPartyLink, Long> {

    Optional<BusinessPartyLink> findByFirmIdAndCustomerId(Long firmId, Long customerId);

    Optional<BusinessPartyLink> findByFirmIdAndPartyId(Long firmId, Long partyId);

    Optional<BusinessPartyLink> findByFirmIdAndCustomerIdAndPartyId(Long firmId, Long customerId, Long partyId);

    List<BusinessPartyLink> findByFirmId(Long firmId);

    List<BusinessPartyLink> findByFirmIdAndStatus(Long firmId, String status);

    Optional<BusinessPartyLink> findByFirmIdAndActiveCustomerId(Long firmId, Long activeCustomerId);

    Optional<BusinessPartyLink> findByFirmIdAndActivePartyId(Long firmId, Long activePartyId);

    boolean existsByFirmIdAndActiveCustomerId(Long firmId, Long activeCustomerId);

    boolean existsByFirmIdAndActivePartyId(Long firmId, Long activePartyId);

    Optional<BusinessPartyLink> findByFirmIdAndCustomerIdAndStatus(Long firmId, Long customerId, String status);

    Optional<BusinessPartyLink> findByFirmIdAndPartyIdAndStatus(Long firmId, Long partyId, String status);

    List<BusinessPartyLink> findByFirmIdAndCustomerIdOrderByLinkedAtDesc(Long firmId, Long customerId);

    List<BusinessPartyLink> findByFirmIdAndPartyIdOrderByLinkedAtDesc(Long firmId, Long partyId);

    boolean existsByFirmIdAndCustomerId(Long firmId, Long customerId);

    boolean existsByFirmIdAndPartyId(Long firmId, Long partyId);

    boolean existsByCustomerId(Long customerId);

    boolean existsByPartyId(Long partyId);

    void deleteByFirmIdAndCustomerIdAndPartyId(Long firmId, Long customerId, Long partyId);

    @Query("SELECT COALESCE(MAX(b.relationshipVersion), 0) FROM BusinessPartyLink b WHERE b.firmId = :firmId AND b.customerId = :customerId AND b.partyId = :partyId")
    Integer findMaxRelationshipVersion(@Param("firmId") Long firmId, @Param("customerId") Long customerId, @Param("partyId") Long partyId);
}
