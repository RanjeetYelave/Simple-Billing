package com.billing.simple.billsoft.repo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByFirmId(Long firmId);

    List<Product> findByFirmIdOrderByNameAsc(Long firmId);

    Page<Product> findByFirmId(Long firmId, Pageable pageable);

    Page<Product> findByFirmIdAndNameContainingIgnoreCase(Long firmId, String name, Pageable pageable);

    Optional<Product> findByIdAndFirmId(Long id, Long firmId);

    boolean existsByIdAndFirmId(Long id, Long firmId);

    void deleteByIdAndFirmId(Long id, Long firmId);

    long countByFirmId(Long firmId);

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.firmId = :firmId AND p.category IS NOT NULL AND TRIM(p.category) != '' ORDER BY p.category ASC")
    List<String> findDistinctCategoriesByFirmId(@Param("firmId") Long firmId);

    List<Product> findTop5ByFirmIdAndNameContainingIgnoreCaseOrderByNameAsc(Long firmId, String name);

    @Query("SELECT p FROM Product p WHERE p.firmId = :firmId AND (p.itemType IS NULL OR p.itemType = 'GOODS') AND p.stockQuantity <= p.minStockLevel ORDER BY p.stockQuantity ASC")
    List<Product> findLowStockProductsByFirmId(@Param("firmId") Long firmId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Product p SET p.preferredPartyId = :partyId WHERE p.id = :productId AND p.firmId = :firmId")
    void updatePreferredPartyId(@Param("productId") Long productId, @Param("partyId") Long partyId, @Param("firmId") Long firmId);
}