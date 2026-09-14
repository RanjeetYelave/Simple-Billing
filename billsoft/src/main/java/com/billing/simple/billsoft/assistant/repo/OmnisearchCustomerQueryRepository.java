package com.billing.simple.billsoft.assistant.repo;

import com.billing.simple.billsoft.entities.Customer;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dedicated, strictly read-only query repository for Customer entity resolution and Quick Help.
 * Contains ZERO mutation methods (no save, no delete, no modifying queries).
 */
@Transactional(readOnly = true)
public interface OmnisearchCustomerQueryRepository extends Repository<Customer, Long> {

    interface CustomerCandidateProjection {
        Long getId();
        String getName();
        String getPhone();
        String getAddress();
        String getGstin();
    }

    @Query("SELECT c.id AS id, c.name AS name, c.phone AS phone, c.address AS address, c.gstin AS gstin " +
           "FROM Customer c WHERE c.firmId = :firmId AND LOWER(c.name) = LOWER(:name)")
    List<CustomerCandidateProjection> findExactByName(@Param("firmId") Long firmId, @Param("name") String name);

    @Query("SELECT c.id AS id, c.name AS name, c.phone AS phone, c.address AS address, c.gstin AS gstin " +
           "FROM Customer c WHERE c.firmId = :firmId AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR c.phone LIKE CONCAT('%', :query, '%'))")
    List<CustomerCandidateProjection> searchCandidates(@Param("firmId") Long firmId, @Param("query") String query);

    @Query("SELECT c.id AS id, c.name AS name, c.phone AS phone, c.address AS address, c.gstin AS gstin " +
           "FROM Customer c WHERE c.firmId = :firmId ORDER BY c.id DESC")
    List<CustomerCandidateProjection> findAllForFirm(@Param("firmId") Long firmId);
}
