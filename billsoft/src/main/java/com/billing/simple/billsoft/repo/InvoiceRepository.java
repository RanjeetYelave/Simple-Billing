package com.billing.simple.billsoft.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.Invoice;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

	// Optional: To generate next invoice number
	Invoice findTopByOrderByIdDesc();
}
