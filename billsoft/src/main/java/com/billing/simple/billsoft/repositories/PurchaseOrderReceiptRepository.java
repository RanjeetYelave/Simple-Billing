package com.billing.simple.billsoft.repositories;

import com.billing.simple.billsoft.entities.PurchaseOrderReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderReceiptRepository extends JpaRepository<PurchaseOrderReceipt, Long> {

    List<PurchaseOrderReceipt> findByPurchaseOrderIdAndFirmIdOrderByReceiptDateDescCreatedAtDesc(Long purchaseOrderId, Long firmId);

    List<PurchaseOrderReceipt> findByFirmIdOrderByReceiptDateDescCreatedAtDesc(Long firmId);

    Optional<PurchaseOrderReceipt> findByIdAndFirmId(Long id, Long firmId);

    Optional<PurchaseOrderReceipt> findByIdempotencyKeyAndFirmId(String idempotencyKey, Long firmId);

    long countByFirmId(Long firmId);
}
