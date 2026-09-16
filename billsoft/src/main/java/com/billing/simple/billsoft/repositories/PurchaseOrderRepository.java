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

    org.springframework.data.domain.Page<PurchaseOrder> findByFirmId(Long firmId, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT po.party.id, SUM(COALESCE(po.totalAmount, 0)), COUNT(po.id) " +
            "FROM PurchaseOrder po " +
            "WHERE po.firmId = :firmId AND po.party.id IN (:partyIds) AND po.status <> com.billing.simple.billsoft.entities.PurchaseOrderStatus.CANCELLED " +
            "GROUP BY po.party.id")
    List<Object[]> sumTotalsByPartyIds(@org.springframework.data.repository.query.Param("firmId") Long firmId,
                                       @org.springframework.data.repository.query.Param("partyIds") List<Long> partyIds);

    @org.springframework.data.jpa.repository.Query("SELECT po.party.id, COUNT(po.id) " +
            "FROM PurchaseOrder po " +
            "WHERE po.firmId = :firmId AND po.party.id IN (:partyIds) AND po.status IN (com.billing.simple.billsoft.entities.PurchaseOrderStatus.DRAFT, com.billing.simple.billsoft.entities.PurchaseOrderStatus.ISSUED) " +
            "GROUP BY po.party.id")
    List<Object[]> countPendingByPartyIds(@org.springframework.data.repository.query.Param("firmId") Long firmId,
                                         @org.springframework.data.repository.query.Param("partyIds") List<Long> partyIds);

    Optional<PurchaseOrder> findByIdAndFirmId(Long id, Long firmId);

    @org.springframework.data.jpa.repository.Query("SELECT po FROM PurchaseOrder po WHERE po.firmId = :firmId AND po.party.id = :partyId ORDER BY po.poDate DESC, po.id DESC")
    List<PurchaseOrder> findByFirmIdAndPartyIdOrderByPoDateDescIdDesc(@org.springframework.data.repository.query.Param("firmId") Long firmId, @org.springframework.data.repository.query.Param("partyId") Long partyId);

    @org.springframework.data.jpa.repository.Query("SELECT po FROM PurchaseOrder po WHERE po.firmId = :firmId AND po.party.id = :partyId AND po.poDate BETWEEN :startDate AND :endDate ORDER BY po.poDate ASC, po.id ASC")
    List<PurchaseOrder> findByFirmIdAndPartyIdAndPoDateBetweenOrderByPoDateAscIdAsc(
            @org.springframework.data.repository.query.Param("firmId") Long firmId,
            @org.springframework.data.repository.query.Param("partyId") Long partyId,
            @org.springframework.data.repository.query.Param("startDate") LocalDate startDate,
            @org.springframework.data.repository.query.Param("endDate") LocalDate endDate);

    List<PurchaseOrder> findByFirmIdAndPoDateBetweenOrderByPoDateAscIdAsc(
            Long firmId, LocalDate startDate, LocalDate endDate);

    @org.springframework.data.jpa.repository.Query("SELECT po FROM PurchaseOrder po WHERE po.firmId = :firmId AND po.party.id = :partyId AND po.poDate < :date")
    List<PurchaseOrder> findByFirmIdAndPartyIdAndPoDateBefore(@org.springframework.data.repository.query.Param("firmId") Long firmId, @org.springframework.data.repository.query.Param("partyId") Long partyId, @org.springframework.data.repository.query.Param("date") LocalDate date);

    List<PurchaseOrder> findByFirmIdAndStatusOrderByPoDateDescIdDesc(Long firmId, PurchaseOrderStatus status);

    long countByFirmId(Long firmId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(po) FROM PurchaseOrder po WHERE po.firmId = :firmId AND po.party.id = :partyId")
    long countByFirmIdAndPartyId(@org.springframework.data.repository.query.Param("firmId") Long firmId, @org.springframework.data.repository.query.Param("partyId") Long partyId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(po) FROM PurchaseOrder po WHERE po.firmId = :firmId AND po.party.id = :partyId AND po.status = :status")
    long countByFirmIdAndPartyIdAndStatus(@org.springframework.data.repository.query.Param("firmId") Long firmId, @org.springframework.data.repository.query.Param("partyId") Long partyId, @org.springframework.data.repository.query.Param("status") PurchaseOrderStatus status);

    Optional<PurchaseOrder> findTopByFirmIdOrderByIdDesc(Long firmId);

    @org.springframework.data.jpa.repository.Query("SELECT po FROM PurchaseOrder po WHERE po.status = com.billing.simple.billsoft.entities.PurchaseOrderStatus.ISSUED AND po.expectedDeliveryDate IS NOT NULL AND po.expectedDeliveryDate <= :today")
    List<PurchaseOrder> findPendingDeliveries(@org.springframework.data.repository.query.Param("today") LocalDate today);

    @org.springframework.data.jpa.repository.Query("SELECT poi.productId, po.party.id, po.partyName, po.poNumber, po.poDate, poi.quantity " +
            "FROM PurchaseOrderItem poi JOIN poi.purchaseOrder po " +
            "WHERE po.firmId = :firmId AND poi.productId IS NOT NULL AND po.status <> com.billing.simple.billsoft.entities.PurchaseOrderStatus.CANCELLED " +
            "ORDER BY poi.productId, po.poDate DESC, po.id DESC")
    List<Object[]> findProductVendorHistoryRaw(@org.springframework.data.repository.query.Param("firmId") Long firmId);
}
