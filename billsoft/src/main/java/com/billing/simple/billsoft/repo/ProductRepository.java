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

    List<Product> findByFirmIdAndIsArchivedFalseOrderByNameAsc(Long firmId);

    List<Product> findByFirmIdAndInInventoryTrueOrderByNameAsc(Long firmId);

    List<Product> findByFirmIdAndInInventoryTrueAndIsArchivedFalseOrderByNameAsc(Long firmId);

    List<Product> findByFirmIdAndInInventoryFalseAndIsArchivedFalseOrderByNameAsc(Long firmId);

    Page<Product> findByFirmId(Long firmId, Pageable pageable);

    Page<Product> findByFirmIdAndInInventoryTrue(Long firmId, Pageable pageable);

    Page<Product> findByFirmIdAndInInventoryTrueAndIsArchivedFalse(Long firmId, Pageable pageable);

    Page<Product> findByFirmIdAndNameContainingIgnoreCase(Long firmId, String name, Pageable pageable);

    Page<Product> findByFirmIdAndInInventoryTrueAndNameContainingIgnoreCase(Long firmId, String name, Pageable pageable);

    Page<Product> findByFirmIdAndInInventoryTrueAndIsArchivedFalseAndNameContainingIgnoreCase(Long firmId, String name, Pageable pageable);

    Optional<Product> findByIdAndFirmId(Long id, Long firmId);

    List<Product> findByIdInAndFirmId(java.util.Collection<Long> ids, Long firmId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.firmId = :firmId")
    Optional<Product> findByIdAndFirmIdForUpdate(@Param("id") Long id, @Param("firmId") Long firmId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids AND p.firmId = :firmId ORDER BY p.id ASC")
    List<Product> findByIdInAndFirmIdForUpdate(@Param("ids") java.util.Collection<Long> ids, @Param("firmId") Long firmId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id ASC")
    List<Product> findByIdInForUpdate(@Param("ids") java.util.Collection<Long> ids);

    boolean existsByIdAndFirmId(Long id, Long firmId);

    void deleteByIdAndFirmId(Long id, Long firmId);

    long countByFirmId(Long firmId);

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.firmId = :firmId AND (p.inInventory IS NULL OR p.inInventory = true) AND p.category IS NOT NULL AND TRIM(p.category) != '' ORDER BY p.category ASC")
    List<String> findDistinctCategoriesByFirmId(@Param("firmId") Long firmId);

    List<Product> findTop5ByFirmIdAndNameContainingIgnoreCaseOrderByNameAsc(Long firmId, String name);

    List<Product> findTop5ByFirmIdAndIsArchivedFalseAndNameContainingIgnoreCaseOrderByNameAsc(Long firmId, String name);

    List<Product> findByFirmIdAndSku(Long firmId, String sku);

    Optional<Product> findFirstByFirmIdAndSku(Long firmId, String sku);

    boolean existsByFirmIdAndSku(Long firmId, String sku);

    Optional<Product> findFirstByBarcode(String barcode);

    Optional<Product> findFirstByFirmIdAndBarcode(Long firmId, String barcode);

    boolean existsByFirmIdAndBarcode(Long firmId, String barcode);

    List<Product> findByFirmIdAndHsnCode(Long firmId, String hsnCode);

    Optional<Product> findFirstByFirmIdAndHsnCode(Long firmId, String hsnCode);

    List<Product> findByFirmIdAndNameIgnoreCase(Long firmId, String name);

    Optional<Product> findFirstByFirmIdAndNameIgnoreCase(Long firmId, String name);

    @Query("SELECT p FROM Product p WHERE p.firmId = :firmId AND (p.inInventory IS NULL OR p.inInventory = true) AND (p.isArchived = false OR p.isArchived IS NULL) AND (p.itemType IS NULL OR p.itemType = 'GOODS') AND p.stockQuantity <= p.minStockLevel ORDER BY p.stockQuantity ASC")
    List<Product> findLowStockProductsByFirmId(@Param("firmId") Long firmId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Product p SET p.preferredPartyId = :partyId WHERE p.id = :productId AND p.firmId = :firmId")
    void updatePreferredPartyId(@Param("productId") Long productId, @Param("partyId") Long partyId, @Param("firmId") Long firmId);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.canonicalProductId = :primaryId WHERE p.firmId = :firmId AND p.canonicalProductId = :secondaryId")
    int updateCanonicalProductIdForMerged(@Param("firmId") Long firmId, @Param("secondaryId") Long secondaryId, @Param("primaryId") Long primaryId);
}