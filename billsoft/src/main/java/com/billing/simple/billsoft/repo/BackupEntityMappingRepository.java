package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.BackupEntityMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
    Optional<Long> findMappedTargetEntityId(
            @Param("backupSourceId") String backupSourceId,
            @Param("sourceEntityId") Long sourceEntityId,
            @Param("entityType") String entityType,
            @Param("targetFirmId") Long targetFirmId);

    void deleteAllByTargetFirmId(Long targetFirmId);
}
