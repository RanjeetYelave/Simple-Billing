package com.billing.simple.billsoft.repositories;

import com.billing.simple.billsoft.entities.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartyRepository extends JpaRepository<Party, Long> {

    List<Party> findByFirmIdOrderByNameAsc(Long firmId);

    Optional<Party> findByIdAndFirmId(Long id, Long firmId);

    List<Party> findByFirmIdAndNameContainingIgnoreCase(Long firmId, String name);

    long countByFirmId(Long firmId);
}
