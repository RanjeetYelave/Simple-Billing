package com.billing.simple.billsoft.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.Invoice;


@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // Existing
    Invoice findTopByOrderByIdDesc();

    // NEW — For Analytics Screen

    /**
     * Get all invoices for a customer (paid + unpaid)
     */
    List<Invoice> findByCustomerIdOrderByInvoiceDateDesc(Long customerId);

    /**
     * Get all PAID invoices for a customer
     */
    List<Invoice> findByCustomerIdAndPaidTrueOrderByInvoiceDateDesc(Long customerId);

    /**
     * Get all UNPAID invoices for a customer
     */
    List<Invoice> findByCustomerIdAndPaidFalseOrderByInvoiceDateDesc(Long customerId);
}
