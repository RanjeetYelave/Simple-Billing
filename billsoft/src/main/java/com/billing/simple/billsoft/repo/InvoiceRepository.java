package com.billing.simple.billsoft.repo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.Invoice;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // existing
    Invoice findTopByOrderByIdDesc();

    // existing (correct for analytics)
    List<Invoice> findByCustomer_Id(Long customerId);

    // existing (analytics search)
    List<Invoice> findByCustomer_NameContainingIgnoreCase(String namePart);

    /* =====================================================
       🆕 REQUIRED FOR STATEMENT SERVICE
       ===================================================== */

    // 1) For "customer statement → all invoices"
    List<Invoice> findAllByCustomer_IdOrderByInvoiceDateAsc(Long customerId);

    // 2) For "customer statement → invoices BEFORE date" (opening balance)
    List<Invoice> findAllByCustomer_IdAndInvoiceDateBefore(Long customerId, LocalDate before);

    // 3) For "customer statement → invoices in date range"
    List<Invoice> findAllByCustomer_IdAndInvoiceDateBetweenOrderByInvoiceDateAsc(
            Long customerId,
            LocalDate from,
            LocalDate to
    );

    // 4) For "firm statement → invoices in date range"
    List<Invoice> findAllByInvoiceDateBetweenOrderByInvoiceDateAsc(
            LocalDate from,
            LocalDate to
    );
}
