package com.billing.simple.billsoft.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
}
