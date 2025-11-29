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
    // Basic Lookups
    // --------------------------------------------
    Invoice findTopByOrderByIdDesc();

    List<Invoice> findByCustomer_Id(Long customerId);

    List<Invoice> findByCustomer_NameContainingIgnoreCase(String namePart);

    // --------------------------------------------
    // Customer Statement Queries
    // --------------------------------------------
    List<Invoice> findAllByCustomer_IdOrderByInvoiceDateAsc(Long customerId);

    List<Invoice> findAllByCustomer_IdAndInvoiceDateBeforeOrderByInvoiceDateAsc(
            Long customerId,
            LocalDateTime before
    );

    List<Invoice> findAllByCustomer_IdAndInvoiceDateBetweenOrderByInvoiceDateAsc(
            Long customerId,
            LocalDateTime from,
            LocalDateTime to
    );

    // --------------------------------------------
    // Firm Statement Queries
    // --------------------------------------------
    List<Invoice> findAllByInvoiceDateBetweenOrderByInvoiceDateAsc(
            LocalDateTime from,
            LocalDateTime to
    );

    // --------------------------------------------
    // Status-Based Queries (Invoices vs Estimates)
    // --------------------------------------------
    List<Invoice> findAllByStatusOrderByInvoiceDateAsc(InvoiceStatus status);

    List<Invoice> findAllByStatusInOrderByInvoiceDateAsc(List<InvoiceStatus> statuses);

    List<Invoice> findAllByCustomer_IdAndStatusOrderByInvoiceDateAsc(
            Long customerId,
            InvoiceStatus status
    );

    // --------------------------------------------
    // Estimate → Invoice Conversion
    // --------------------------------------------
    List<Invoice> findAllByStatusAndConvertedInvoiceIdIsNullOrderByInvoiceDateAsc(
            InvoiceStatus status
    );

    List<Invoice> findAllByConvertedInvoiceId(Long invoiceId);
}
