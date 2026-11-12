package com.billing.simple.billsoft.repositories;

import com.billing.simple.billsoft.entities.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartyRepository extends JpaRepository<Party, Long> {

    List<Party> findByFirmIdOrderByNameAsc(Long firmId);

    org.springframework.data.domain.Page<Party> findByFirmId(Long firmId, org.springframework.data.domain.Pageable pageable);

    Optional<Party> findByIdAndFirmId(Long id, Long firmId);

    boolean existsByIdAndFirmId(Long id, Long firmId);

    void deleteByIdAndFirmId(Long id, Long firmId);

    List<Party> findByFirmIdAndNameContainingIgnoreCase(Long firmId, String name);

    Optional<Party> findFirstByFirmIdAndNameIgnoreCase(Long firmId, String name);

    Optional<Party> findFirstByFirmIdAndGstinIgnoreCase(Long firmId, String gstin);

    Optional<Party> findFirstByFirmIdAndPhone(Long firmId, String phone);

    Optional<Party> findFirstByFirmIdAndPhoneAndNameIgnoreCase(Long firmId, String phone, String name);

    Optional<Party> findFirstByFirmIdAndNameIgnoreCaseAndPhoneIsNull(Long firmId, String name);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM Party p WHERE p.firmId = :firmId AND (" +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "p.phone LIKE CONCAT('%', :query, '%') OR " +
            "LOWER(p.gstin) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.contactPerson) LIKE LOWER(CONCAT('%', :query, '%'))" +
            ") ORDER BY p.name ASC")
    List<Party> searchByFirmIdAndQuery(@org.springframework.data.repository.query.Param("firmId") Long firmId,
                                       @org.springframework.data.repository.query.Param("query") String query);

    long countByFirmId(Long firmId);
}
