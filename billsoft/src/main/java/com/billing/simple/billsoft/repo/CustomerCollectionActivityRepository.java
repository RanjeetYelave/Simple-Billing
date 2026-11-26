package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.CustomerCollectionActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerCollectionActivityRepository extends JpaRepository<CustomerCollectionActivity, Long> {

    List<CustomerCollectionActivity> findByFirmIdAndCustomerIdOrderByCreatedAtDesc(Long firmId, Long customerId);

    List<CustomerCollectionActivity> findByFirmIdOrderByCreatedAtDesc(Long firmId);

    List<CustomerCollectionActivity> findByFirmIdAndCustomerId(Long firmId, Long customerId);
}
