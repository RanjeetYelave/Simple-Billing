package com.billing.simple.billsoft.assistant.repo;

import com.billing.simple.billsoft.entities.Product;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dedicated read-only query repository for Product entity resolution and stock lookup.
 */
@Transactional(readOnly = true)
public interface OmnisearchProductQueryRepository extends Repository<Product, Long> {

    interface ProductCandidateProjection {
        Long getId();
        String getName();
        BigDecimal getStockQuantity();
        BigDecimal getPrice();
    }

    @Query("SELECT p.id AS id, p.name AS name, p.stockQuantity AS stockQuantity, p.price AS price " +
           "FROM Product p WHERE p.firmId = :firmId AND LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<ProductCandidateProjection> searchCandidates(@Param("firmId") Long firmId, @Param("query") String query);
}
