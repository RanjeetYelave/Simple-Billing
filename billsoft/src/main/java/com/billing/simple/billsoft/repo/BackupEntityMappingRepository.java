package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.BackupEntityMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BackupEntityMappingRepository extends JpaRepository<BackupEntityMapping, Long> {

    Optional<BackupEntityMapping> findFirstByBackupSourceIdAndSourceEntityIdAndEntityTypeAndTargetFirmId(
            String backupSourceId, Long sourceEntityId, String entityType, Long targetFirmId);

    default Optional<BackupEntityMapping> findByBackupSourceIdAndSourceEntityIdAndEntityTypeAndTargetFirmId(
            String backupSourceId, Long sourceEntityId, String entityType, Long targetFirmId) {
        return findFirstByBackupSourceIdAndSourceEntityIdAndEntityTypeAndTargetFirmId(backupSourceId, sourceEntityId, entityType, targetFirmId);
    }

    @Query("SELECT m.targetEntityId FROM BackupEntityMapping m WHERE m.backupSourceId = :backupSourceId AND m.sourceEntityId = :sourceEntityId AND m.entityType = :entityType AND m.targetFirmId = :targetFirmId")
    List<Long> findMappedTargetEntityIds(
            @Param("backupSourceId") String backupSourceId,
            @Param("sourceEntityId") Long sourceEntityId,
            @Param("entityType") String entityType,
            @Param("targetFirmId") Long targetFirmId);

    default Optional<Long> findMappedTargetEntityId(
            String backupSourceId,
            Long sourceEntityId,
            String entityType,
            Long targetFirmId) {
        List<Long> ids = findMappedTargetEntityIds(backupSourceId, sourceEntityId, entityType, targetFirmId);
        return (ids == null || ids.isEmpty()) ? Optional.empty() : Optional.of(ids.get(0));
    }

    void deleteAllByTargetFirmId(Long targetFirmId);
}
