package com.billing.simple.billsoft.repositories;

import com.billing.simple.billsoft.entities.PurchaseOrderBatchRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderBatchRecordRepository extends JpaRepository<PurchaseOrderBatchRecord, Long> {

    Optional<PurchaseOrderBatchRecord> findByFirmIdAndSupplierOpId(Long firmId, String supplierOpId);

    List<PurchaseOrderBatchRecord> findByFirmId(Long firmId);

    List<PurchaseOrderBatchRecord> findAllByFirmIdAndClientRequestId(Long firmId, String clientRequestId);

    boolean existsByFirmIdAndClientRequestIdAndStatus(Long firmId, String clientRequestId, String status);
}
