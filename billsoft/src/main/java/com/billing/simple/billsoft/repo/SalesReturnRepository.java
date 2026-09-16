package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.SalesReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {

    List<SalesReturn> findByFirmIdOrderByReturnDateDescCreatedAtDesc(Long firmId);

    List<SalesReturn> findByFirmIdOrderByReturnDateDescIdDesc(Long firmId);

    Page<SalesReturn> findByFirmIdOrderByReturnDateDescCreatedAtDesc(Long firmId, Pageable pageable);

    Optional<SalesReturn> findByIdAndFirmId(Long id, Long firmId);

    boolean existsByIdAndFirmId(Long id, Long firmId);

    void deleteByIdAndFirmId(Long id, Long firmId);

    List<SalesReturn> findByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);

    List<SalesReturn> findByInvoiceIdAndFirmIdOrderByCreatedAtDesc(Long invoiceId, Long firmId);

    List<SalesReturn> findByCustomerIdOrderByReturnDateDesc(Long customerId);

    List<SalesReturn> findByCustomerIdOrderByReturnDateAscIdAsc(Long customerId);

    List<SalesReturn> findByFirmIdAndCustomerIdOrderByReturnDateAscIdAsc(Long firmId, Long customerId);

    List<SalesReturn> findByFirmIdAndReturnDateBetween(Long firmId, LocalDate start, LocalDate end);

    @org.springframework.data.jpa.repository.Query("SELECT sr.invoice.id, SUM(COALESCE(sr.totalRefundAmount, 0)) FROM SalesReturn sr WHERE sr.firmId = :firmId GROUP BY sr.invoice.id")
    List<Object[]> sumRefundTotalsByInvoiceForFirm(@org.springframework.data.repository.query.Param("firmId") Long firmId);

    @org.springframework.data.jpa.repository.Query("SELECT sr.invoice.id, SUM(COALESCE(sr.totalRefundAmount, 0)) FROM SalesReturn sr GROUP BY sr.invoice.id")
    List<Object[]> sumRefundTotalsByInvoice();

    @org.springframework.data.jpa.repository.Query("SELECT sr.customer.id, SUM(COALESCE(sr.totalRefundAmount, 0)) FROM SalesReturn sr WHERE sr.firmId = :firmId AND sr.customer IS NOT NULL GROUP BY sr.customer.id")
    List<Object[]> sumRefundTotalsByCustomerForFirm(@org.springframework.data.repository.query.Param("firmId") Long firmId);

    long countByFirmId(Long firmId);
}

