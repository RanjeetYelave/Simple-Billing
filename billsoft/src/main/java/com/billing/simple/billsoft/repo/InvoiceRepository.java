package com.billing.simple.billsoft.repo;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceStatus;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByFirmId(Long firmId);
    
    // Used by BackupService
    List<Invoice> findAllByFirmId(Long firmId);
    
    long countByFirmId(Long firmId);
    
    long countByFirmIdAndStatus(Long firmId, InvoiceStatus status);
    
    List<Invoice> findByCustomer_Id(Long customerId);
    
    // Used by StatementServiceImpl
    List<Invoice> findByFirmIdAndCustomer_Id(Long firmId, Long customerId);
    
    // Used by StatementServiceImpl
    List<Invoice> findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(Long firmId, LocalDateTime from, LocalDateTime to);
    
    // Used by StatementServiceImpl
    List<Invoice> findAllByInvoiceDateBetweenOrderByInvoiceDateAsc(LocalDateTime from, LocalDateTime to);

    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items WHERE i.firmId = :firmId AND LOWER(i.customer.name) LIKE LOWER(CONCAT('%',:name,'%'))")
    List<Invoice> findByFirmIdAndCustomerNameContainingIgnoreCase(@Param("firmId") Long firmId, @Param("name") String name);

    // Paginated queries
    Page<Invoice> findByFirmId(Long firmId, Pageable pageable);
    Page<Invoice> findByFirmIdAndStatus(Long firmId, InvoiceStatus status, Pageable pageable);
    Page<Invoice> findByFirmIdAndStatusIn(Long firmId, List<InvoiceStatus> statuses, Pageable pageable);

    List<Invoice> findByCustomer_NameContainingIgnoreCase(String name);
    
    List<Invoice> findAllByFirmIdAndStatusOrderByInvoiceDateAsc(Long firmId, InvoiceStatus status);
    
    List<Invoice> findAllByFirmIdAndStatusIn(Long firmId, List<InvoiceStatus> statuses);

    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items")
    List<Invoice> findAllWithItems();
    
    List<Invoice> findAllByStatusOrderByInvoiceDateAsc(InvoiceStatus status);
    
    // For quotation conversion lookup
    Invoice findByConvertedInvoiceId(Long convertedInvoiceId);

    // ── Aggregation queries for analytics ──
    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.firmId = :firmId AND i.status IN :statuses")
    long countByFirmIdAndStatusIn(@Param("firmId") Long firmId, @Param("statuses") List<InvoiceStatus> statuses);

    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i WHERE i.firmId = :firmId AND i.paid = :paid")
    double sumTotalAmountByFirmIdAndPaid(@Param("firmId") Long firmId, @Param("paid") boolean paid);

    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i WHERE i.firmId = :firmId AND i.status IN :statuses")
    double sumTotalAmountByFirmIdAndStatusIn(@Param("firmId") Long firmId, @Param("statuses") List<InvoiceStatus> statuses);

    @Query("SELECT i.invoiceNumber FROM Invoice i WHERE i.firmId = :firmId AND i.invoiceNumber IS NOT NULL")
    List<String> findInvoiceNumbersByFirmId(@Param("firmId") Long firmId);

    @Query("SELECT i.estimateNumber FROM Invoice i WHERE i.firmId = :firmId AND i.estimateNumber IS NOT NULL")
    List<String> findEstimateNumbersByFirmId(@Param("firmId") Long firmId);
}
