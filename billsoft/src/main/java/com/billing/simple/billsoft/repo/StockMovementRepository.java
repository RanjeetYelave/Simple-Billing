package com.billing.simple.billsoft.repo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.StockMovement;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByFirmIdOrderByCreatedAtDesc(Long firmId);

    List<StockMovement> findByProductIdAndFirmIdOrderByCreatedAtDesc(Long productId, Long firmId);

    List<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId);

    Page<StockMovement> findByFirmId(Long firmId, Pageable pageable);

    Page<StockMovement> findByProductIdAndFirmId(Long productId, Long firmId, Pageable pageable);

    Page<StockMovement> findByProductId(Long productId, Pageable pageable);

    Optional<StockMovement> findByIdAndFirmId(Long id, Long firmId);

    boolean existsByIdAndFirmId(Long id, Long firmId);

    void deleteByIdAndFirmId(Long id, Long firmId);

    long countByFirmId(Long firmId);

    List<StockMovement> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);
}

