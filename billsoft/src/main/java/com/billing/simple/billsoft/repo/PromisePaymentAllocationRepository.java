package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.PromisePaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromisePaymentAllocationRepository extends JpaRepository<PromisePaymentAllocation, Long> {
    List<PromisePaymentAllocation> findByFirmId(Long firmId);
    List<PromisePaymentAllocation> findByFirmIdAndPromiseId(Long firmId, Long promiseId);
    List<PromisePaymentAllocation> findByFirmIdAndPaymentId(Long firmId, Long paymentId);
    List<PromisePaymentAllocation> findByPaymentId(Long paymentId);
    void deleteByFirmIdAndPaymentId(Long firmId, Long paymentId);
    void deleteByPaymentId(Long paymentId);
}
