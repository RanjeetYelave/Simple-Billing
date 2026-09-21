package com.billing.simple.billsoft.repo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.billing.simple.billsoft.entities.SavedItem;

@Repository
public interface SavedItemRepository extends JpaRepository<SavedItem, Long> {

    List<SavedItem> findByFirmIdAndIsArchivedFalse(Long firmId);

    List<SavedItem> findByFirmIdAndIsArchivedFalseOrderByUsageCountDescLastUsedAtDesc(Long firmId);

    Optional<SavedItem> findByIdAndFirmId(Long id, Long firmId);

    Optional<SavedItem> findByFirmIdAndNormalizedNameAndIsArchivedFalse(Long firmId, String normalizedName);

    List<SavedItem> findByFirmIdAndNameContainingIgnoreCaseAndIsArchivedFalse(Long firmId, String name);

    Page<SavedItem> findByFirmIdAndIsArchivedFalse(Long firmId, Pageable pageable);

    @Query("SELECT s FROM SavedItem s WHERE s.firmId = :firmId AND s.isArchived = false AND s.canonicalProductId IS NULL ORDER BY s.usageCount DESC, s.lastUsedAt DESC")
    List<SavedItem> findActiveUnpromotedByFirmId(@Param("firmId") Long firmId);

    long countByFirmIdAndIsArchivedFalse(Long firmId);
}
