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

    // --------------------------------------------
    // Basic Lookups (firm-scoped) with FETCH JOIN to avoid N+1
    // --------------------------------------------
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items it LEFT JOIN FETCH it.product WHERE i.firmId = :firmId")
    List<Invoice> findAllByFirmId(@Param("firmId") Long firmId);

    Invoice findTopByFirmIdOrderByIdDesc(Long firmId);

    List<Invoice> findByFirmIdAndCustomer_Id(Long firmId, Long customerId);

    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items it LEFT JOIN FETCH it.product WHERE i.firmId = :firmId AND (i.customer IS NULL OR LOWER(i.customer.name) LIKE LOWER(CONCAT('%', :namePart, '%')))")
    List<Invoice> findByFirmIdAndCustomerNameContainingIgnoreCase(@Param("firmId") Long firmId, @Param("namePart") String namePart);

    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items it LEFT JOIN FETCH it.product")
    List<Invoice> findAllWithItems();

    // --------------------------------------------
    // Customer Statement Queries (firm-scoped)
    // --------------------------------------------
    List<Invoice> findAllByFirmIdAndCustomer_IdOrderByInvoiceDateAsc(Long firmId, Long customerId);

    List<Invoice> findAllByFirmIdAndCustomer_IdAndInvoiceDateBeforeOrderByInvoiceDateAsc(
            Long firmId, Long customerId, LocalDateTime before);

    List<Invoice> findAllByFirmIdAndCustomer_IdAndInvoiceDateBetweenOrderByInvoiceDateAsc(
            Long firmId, Long customerId, LocalDateTime from, LocalDateTime to);

    // --------------------------------------------
    // Firm Statement Queries (firm-scoped)
    // --------------------------------------------
    List<Invoice> findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(
            Long firmId, LocalDateTime from, LocalDateTime to);

    // --------------------------------------------
    // Status-Based Queries (firm-scoped)
    // --------------------------------------------
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items it LEFT JOIN FETCH it.product WHERE i.firmId = :firmId AND i.status = :status ORDER BY i.invoiceDate ASC")
    List<Invoice> findAllByFirmIdAndStatusOrderByInvoiceDateAsc(@Param("firmId") Long firmId, @Param("status") InvoiceStatus status);

    List<Invoice> findAllByFirmIdAndStatusInOrderByInvoiceDateAsc(Long firmId, List<InvoiceStatus> statuses);

    List<Invoice> findAllByFirmIdAndStatusIn(Long firmId, List<InvoiceStatus> statuses);

    List<Invoice> findAllByFirmIdAndCustomer_IdAndStatusOrderByInvoiceDateAsc(
            Long firmId, Long customerId, InvoiceStatus status);

    // --------------------------------------------
    // Estimate → Invoice Conversion (firm-scoped)
    // --------------------------------------------
    List<Invoice> findAllByFirmIdAndStatusAndConvertedInvoiceIdIsNullOrderByInvoiceDateAsc(
            Long firmId, InvoiceStatus status);

    List<Invoice> findAllByFirmIdAndConvertedInvoiceId(Long firmId, Long invoiceId);

    // --------------------------------------------
    // Count queries
    // --------------------------------------------
    long countByFirmId(Long firmId);
    long countByFirmIdAndStatus(Long firmId, InvoiceStatus status);

    // --------------------------------------------
    // Unscoped queries
    // --------------------------------------------
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items it LEFT JOIN FETCH it.product WHERE i.customer.id = :customerId")
    List<Invoice> findByCustomer_Id(@Param("customerId") Long customerId);

    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items it LEFT JOIN FETCH it.product WHERE LOWER(i.customer.name) LIKE LOWER(CONCAT('%', :namePart, '%'))")
    List<Invoice> findByCustomer_NameContainingIgnoreCase(@Param("namePart") String namePart);

    // Unscoped status queries (used by InvoiceService.getAllEstimates without firmId)
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items it LEFT JOIN FETCH it.product WHERE i.status = :status ORDER BY i.invoiceDate ASC")
    List<Invoice> findAllByStatusOrderByInvoiceDateAsc(@Param("status") InvoiceStatus status);

    // Unscoped date range query (used by StatementServiceImpl)
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items it LEFT JOIN FETCH it.product WHERE i.invoiceDate BETWEEN :from AND :to ORDER BY i.invoiceDate ASC")
    List<Invoice> findAllByInvoiceDateBetweenOrderByInvoiceDateAsc(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.items it LEFT JOIN FETCH it.product ORDER BY i.invoiceDate DESC")
    List<Invoice> findAllOrderByInvoiceDateDesc();
}