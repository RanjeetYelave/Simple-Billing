package com.billing.simple.billsoft.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import com.billing.simple.billsoft.entities.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Invoice findTopByOrderByIdDesc();
}
