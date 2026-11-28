package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.CustomerPromiseToPay;
import com.billing.simple.billsoft.entities.PromiseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerPromiseToPayRepository extends JpaRepository<CustomerPromiseToPay, Long> {

    List<CustomerPromiseToPay> findByFirmIdAndCustomerIdOrderByPromisedDateDesc(Long firmId, Long customerId);

    List<CustomerPromiseToPay> findByFirmIdAndCustomerIdAndStatusIn(Long firmId, Long customerId, Collection<PromiseStatus> statuses);

    List<CustomerPromiseToPay> findByFirmIdAndStatusIn(Long firmId, Collection<PromiseStatus> statuses);

    List<CustomerPromiseToPay> findByFirmId(Long firmId);

    Optional<CustomerPromiseToPay> findByIdAndFirmId(Long id, Long firmId);

    List<CustomerPromiseToPay> findByFirmIdAndCustomerId(Long firmId, Long customerId);

    List<CustomerPromiseToPay> findByFirmIdAndInvoiceId(Long firmId, Long invoiceId);

    List<CustomerPromiseToPay> findByFirmIdAndInvoiceIdAndStatusNotIn(Long firmId, Long invoiceId, Collection<PromiseStatus> statuses);

    List<CustomerPromiseToPay> findByFirmIdOrderByPromisedDateDesc(Long firmId);
}
