package com.billing.simple.billsoft.assistant.repo;

import com.billing.simple.billsoft.entities.Party;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dedicated read-only query repository for Party / Vendor entity resolution.
 */
@Transactional(readOnly = true)
public interface OmnisearchPartyQueryRepository extends Repository<Party, Long> {

    interface PartyCandidateProjection {
        Long getId();
        String getName();
        String getPhone();
        String getContactPerson();
    }

    @Query("SELECT p.id AS id, p.name AS name, p.phone AS phone, p.contactPerson AS contactPerson " +
           "FROM Party p WHERE p.firmId = :firmId AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR p.phone LIKE CONCAT('%', :query, '%'))")
    List<PartyCandidateProjection> searchCandidates(@Param("firmId") Long firmId, @Param("query") String query);
}
