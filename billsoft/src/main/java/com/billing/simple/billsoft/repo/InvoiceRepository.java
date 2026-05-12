package com.billing.simple.billsoft.repo;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.Invoice;
import com.billing.simple.billsoft.entities.InvoiceStatus;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // --------------------------------------------
    // Basic Lookups (firm-scoped)
    // --------------------------------------------
    List<Invoice> findAllByFirmId(Long firmId);

    Invoice findTopByFirmIdOrderByIdDesc(Long firmId);

    List<Invoice> findByFirmIdAndCustomer_Id(Long firmId, Long customerId);

    // Use @Query because ContainingIgnoreCase on a join column (customer.name) needs explicit JPQL
    @org.springframework.data.jpa.repository.Query("SELECT i FROM Invoice i WHERE i.firmId = :firmId AND (i.customer IS NULL OR LOWER(i.customer.name) LIKE LOWER(CONCAT('%', :namePart, '%')))")
    List<Invoice> findByFirmIdAndCustomerNameContainingIgnoreCase(@org.springframework.data.repository.query.Param("firmId") Long firmId, @org.springframework.data.repository.query.Param("namePart") String namePart);

    // --------------------------------------------
    // Customer Statement Queries (firm-scoped)
    // --------------------------------------------
    List<Invoice> findAllByFirmIdAndCustomer_IdOrderByInvoiceDateAsc(Long firmId, Long customerId);

    List<Invoice> findAllByFirmIdAndCustomer_IdAndInvoiceDateBeforeOrderByInvoiceDateAsc(
            Long firmId,
            Long customerId,
            LocalDateTime before
    );

    List<Invoice> findAllByFirmIdAndCustomer_IdAndInvoiceDateBetweenOrderByInvoiceDateAsc(
            Long firmId,
            Long customerId,
            LocalDateTime from,
            LocalDateTime to
    );

    // --------------------------------------------
    // Firm Statement Queries (firm-scoped)
    // --------------------------------------------
    List<Invoice> findAllByFirmIdAndInvoiceDateBetweenOrderByInvoiceDateAsc(
            Long firmId,
            LocalDateTime from,
            LocalDateTime to
    );

    // --------------------------------------------
    // Status-Based Queries (firm-scoped)
    // --------------------------------------------
    List<Invoice> findAllByFirmIdAndStatusOrderByInvoiceDateAsc(Long firmId, InvoiceStatus status);

    List<Invoice> findAllByFirmIdAndStatusInOrderByInvoiceDateAsc(Long firmId, List<InvoiceStatus> statuses);

    List<Invoice> findAllByFirmIdAndStatusIn(Long firmId, List<InvoiceStatus> statuses);

    List<Invoice> findAllByFirmIdAndCustomer_IdAndStatusOrderByInvoiceDateAsc(
            Long firmId,
            Long customerId,
            InvoiceStatus status
    );

    // --------------------------------------------
    // Estimate → Invoice Conversion (firm-scoped)
    // --------------------------------------------
    List<Invoice> findAllByFirmIdAndStatusAndConvertedInvoiceIdIsNullOrderByInvoiceDateAsc(
            Long firmId,
            InvoiceStatus status
    );

    List<Invoice> findAllByFirmIdAndConvertedInvoiceId(Long firmId, Long invoiceId);

    // --------------------------------------------
    // Count queries (for firm-scoped number generation)
    // --------------------------------------------
    long countByFirmId(Long firmId);

    long countByFirmIdAndStatus(Long firmId, InvoiceStatus status);

    // --------------------------------------------
    // Unscoped queries (used by analytics, fallback)
    // --------------------------------------------
    List<Invoice> findByCustomer_Id(Long customerId);

    @org.springframework.data.jpa.repository.Query("SELECT i FROM Invoice i WHERE LOWER(i.customer.name) LIKE LOWER(CONCAT('%', :namePart, '%'))")
    List<Invoice> findByCustomer_NameContainingIgnoreCase(@org.springframework.data.repository.query.Param("namePart") String namePart);

    List<Invoice> findAllByStatusOrderByInvoiceDateAsc(InvoiceStatus status);

    List<Invoice> findAllByInvoiceDateBetweenOrderByInvoiceDateAsc(LocalDateTime from, LocalDateTime to);
}