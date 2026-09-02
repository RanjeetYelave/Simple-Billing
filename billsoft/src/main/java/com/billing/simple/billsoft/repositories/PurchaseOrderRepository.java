package com.billing.simple.billsoft.repositories;

import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    List<PurchaseOrder> findByFirmIdOrderByPoDateDescIdDesc(Long firmId);

    Optional<PurchaseOrder> findByIdAndFirmId(Long id, Long firmId);

    List<PurchaseOrder> findByFirmIdAndPartyIdOrderByPoDateDescIdDesc(Long firmId, Long partyId);

    List<PurchaseOrder> findByFirmIdAndPartyIdAndPoDateBetweenOrderByPoDateAscIdAsc(
            Long firmId, Long partyId, LocalDate startDate, LocalDate endDate);

    List<PurchaseOrder> findByFirmIdAndPoDateBetweenOrderByPoDateAscIdAsc(
            Long firmId, LocalDate startDate, LocalDate endDate);

    List<PurchaseOrder> findByFirmIdAndPartyIdAndPoDateBefore(Long firmId, Long partyId, LocalDate date);

    List<PurchaseOrder> findByFirmIdAndStatusOrderByPoDateDescIdDesc(Long firmId, PurchaseOrderStatus status);

    long countByFirmId(Long firmId);

    long countByFirmIdAndPartyId(Long firmId, Long partyId);

    long countByFirmIdAndPartyIdAndStatus(Long firmId, Long partyId, PurchaseOrderStatus status);

    Optional<PurchaseOrder> findTopByFirmIdOrderByIdDesc(Long firmId);
}
