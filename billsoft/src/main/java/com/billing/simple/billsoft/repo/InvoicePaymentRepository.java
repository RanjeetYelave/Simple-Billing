package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.InvoicePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, Long> {

    List<InvoicePayment> findByInvoiceIdOrderByPaymentDateAscIdAsc(Long invoiceId);

    List<InvoicePayment> findByFirmIdAndPaymentDateBetweenOrderByPaymentDateAscIdAsc(Long firmId, LocalDate from, LocalDate to);

    List<InvoicePayment> findByFirmIdAndCustomerIdOrderByPaymentDateAscIdAsc(Long firmId, Long customerId);

    List<InvoicePayment> findByCustomerIdOrderByPaymentDateAscIdAsc(Long customerId);

    List<InvoicePayment> findByFirmIdOrderByPaymentDateDescIdDesc(Long firmId);
}
