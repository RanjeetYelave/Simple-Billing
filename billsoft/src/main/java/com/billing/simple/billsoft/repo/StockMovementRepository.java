package com.billing.simple.billsoft.repo;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.StockMovement;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByFirmIdOrderByCreatedAtDesc(Long firmId);

    List<StockMovement> findByProductIdAndFirmIdOrderByCreatedAtDesc(Long productId, Long firmId);

    List<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<StockMovement> findByReferenceTypeAndReferenceId(String referenceType, String referenceId);
}
