package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.InvoiceMigrationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceMigrationRecordRepository extends JpaRepository<InvoiceMigrationRecord, Long> {

    List<InvoiceMigrationRecord> findByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);

    Optional<InvoiceMigrationRecord> findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(Long invoiceId, String status);

    Optional<InvoiceMigrationRecord> findFirstByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);

    List<InvoiceMigrationRecord> findBySourceFirmIdOrTargetFirmIdOrderByCreatedAtDesc(Long sourceFirmId, Long targetFirmId);

    List<InvoiceMigrationRecord> findAllByOrderByIdAsc();
}
