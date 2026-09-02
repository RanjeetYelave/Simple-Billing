package com.billing.simple.billsoft.repositories;

import com.billing.simple.billsoft.entities.PartyPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PartyPaymentRepository extends JpaRepository<PartyPayment, Long> {

    List<PartyPayment> findByFirmIdAndPartyIdOrderByPaymentDateDescIdDesc(Long firmId, Long partyId);

    List<PartyPayment> findByFirmIdAndPartyIdAndPaymentDateBetweenOrderByPaymentDateAscIdAsc(
            Long firmId, Long partyId, LocalDate startDate, LocalDate endDate);

    List<PartyPayment> findByFirmIdAndPartyIdAndPaymentDateBefore(Long firmId, Long partyId, LocalDate date);

    List<PartyPayment> findByFirmIdOrderByPaymentDateDescIdDesc(Long firmId);

    List<PartyPayment> findByFirmIdAndPaymentDateBetweenOrderByPaymentDateAscIdAsc(
            Long firmId, LocalDate startDate, LocalDate endDate);

    List<PartyPayment> findByPurchaseOrderId(Long purchaseOrderId);

    void deleteByPurchaseOrderId(Long purchaseOrderId);

    void deleteByPartyId(Long partyId);
}
