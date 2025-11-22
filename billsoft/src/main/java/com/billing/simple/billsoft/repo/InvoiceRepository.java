package com.billing.simple.billsoft.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.Invoice;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Invoice findTopByOrderByIdDesc();

    // for analytics by customer id
    List<Invoice> findByCustomer_Id(Long customerId);

    // for analytics search by customer name
    List<Invoice> findByCustomer_NameContainingIgnoreCase(String namePart);
}
