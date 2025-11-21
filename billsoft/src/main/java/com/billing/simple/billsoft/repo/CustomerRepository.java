package com.billing.simple.billsoft.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.Customer;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
