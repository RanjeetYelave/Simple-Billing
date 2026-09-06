package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.SalesReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {

    List<SalesReturn> findByFirmIdOrderByReturnDateDescCreatedAtDesc(Long firmId);

    Page<SalesReturn> findByFirmIdOrderByReturnDateDescCreatedAtDesc(Long firmId, Pageable pageable);

    List<SalesReturn> findByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);

    List<SalesReturn> findByCustomerIdOrderByReturnDateDesc(Long customerId);

    List<SalesReturn> findByFirmIdAndReturnDateBetween(Long firmId, LocalDate start, LocalDate end);

    long countByFirmId(Long firmId);
}
