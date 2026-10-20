package com.billing.simple.billsoft.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.Customer;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByFirmId(Long firmId);

    List<Customer> findByFirmIdOrderByNameAsc(Long firmId);

    Optional<Customer> findByIdAndFirmId(Long id, Long firmId);

    boolean existsByIdAndFirmId(Long id, Long firmId);

    List<Customer> findByFirmIdAndGstinIgnoreCase(Long firmId, String gstin);

    List<Customer> findByFirmIdAndPhone(Long firmId, String phone);

    Optional<Customer> findFirstByFirmIdAndPhone(Long firmId, String phone);

    Optional<Customer> findFirstByFirmIdAndPhoneAndNameIgnoreCase(Long firmId, String phone, String name);

    List<Customer> findByFirmIdAndNameIgnoreCase(Long firmId, String name);

    Optional<Customer> findFirstByFirmIdAndNameIgnoreCase(Long firmId, String name);

    Optional<Customer> findFirstByFirmIdAndNameIgnoreCaseAndPhoneIsNull(Long firmId, String name);

    List<Customer> findTop5ByFirmIdAndNameContainingIgnoreCaseOrderByNameAsc(Long firmId, String name);

    List<Customer> findTop5ByFirmIdAndPhoneContainingOrderByNameAsc(Long firmId, String phone);

    void deleteByIdAndFirmId(Long id, Long firmId);

    long countByFirmId(Long firmId);
}