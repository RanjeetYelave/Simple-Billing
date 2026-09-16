package com.billing.simple.billsoft.repositories;

import com.billing.simple.billsoft.entities.PartyPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PartyPaymentRepository extends JpaRepository<PartyPayment, Long> {

    List<PartyPayment> findByFirmIdAndPartyIdOrderByPaymentDateDescIdDesc(Long firmId, Long partyId);

    @org.springframework.data.jpa.repository.Query("SELECT pay.partyId, SUM(COALESCE(pay.amount, 0)) " +
            "FROM PartyPayment pay " +
            "WHERE pay.firmId = :firmId AND pay.partyId IN (:partyIds) " +
            "GROUP BY pay.partyId")
    List<Object[]> sumPaidByPartyIds(@org.springframework.data.repository.query.Param("firmId") Long firmId,
                                     @org.springframework.data.repository.query.Param("partyIds") List<Long> partyIds);

    List<PartyPayment> findByFirmIdAndPartyIdAndPaymentDateBetweenOrderByPaymentDateAscIdAsc(
            Long firmId, Long partyId, LocalDate startDate, LocalDate endDate);

    List<PartyPayment> findByFirmIdAndPartyIdAndPaymentDateBefore(Long firmId, Long partyId, LocalDate date);

    List<PartyPayment> findByFirmIdOrderByPaymentDateDescIdDesc(Long firmId);

    List<PartyPayment> findByFirmIdAndPaymentDateBetweenOrderByPaymentDateAscIdAsc(
            Long firmId, LocalDate startDate, LocalDate endDate);

    List<PartyPayment> findByPurchaseOrderId(Long purchaseOrderId);

    List<PartyPayment> findByFirmIdAndPartyIdAndPurchaseOrderIdIsNullOrderByPaymentDateDescIdDesc(Long firmId, Long partyId);

    List<PartyPayment> findByFirmIdAndPurchaseOrderIdIsNullOrderByPaymentDateDescIdDesc(Long firmId);

    void deleteByPurchaseOrderId(Long purchaseOrderId);

    void deleteByPartyId(Long partyId);
}
