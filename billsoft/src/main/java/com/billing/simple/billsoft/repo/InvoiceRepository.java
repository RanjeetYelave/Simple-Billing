package com.billing.simple.billsoft.repo;

import java.time.LocalDateTime;
import java.util.List;

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

    List<Invoice> findByCustomer_NameContainingIgnoreCase(String name);
    
    List<Invoice> findAllByFirmIdAndStatusOrderByInvoiceDateAsc(Long firmId, InvoiceStatus status);
    
    List<Invoice> findAllByFirmIdAndStatusIn(Long firmId, List<InvoiceStatus> statuses);

    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items")
    List<Invoice> findAllWithItems();
    
    List<Invoice> findAllByStatusOrderByInvoiceDateAsc(InvoiceStatus status);
    
    // For quotation conversion lookup
    Invoice findByConvertedInvoiceId(Long convertedInvoiceId);
}