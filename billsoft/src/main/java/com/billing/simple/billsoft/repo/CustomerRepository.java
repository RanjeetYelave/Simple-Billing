package com.billing.simple.billsoft.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.Customer;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByFirmIdOrderByNameAsc(Long firmId);

    Optional<Customer> findByIdAndFirmId(Long id, Long firmId);

    boolean existsByIdAndFirmId(Long id, Long firmId);

    Optional<Customer> findFirstByFirmIdAndPhone(Long firmId, String phone);

    Optional<Customer> findFirstByFirmIdAndNameIgnoreCase(Long firmId, String name);

    void deleteByIdAndFirmId(Long id, Long firmId);

    long countByFirmId(Long firmId);
}