package com.billing.simple.billsoft.telegram.repo;

import com.billing.simple.billsoft.telegram.entities.TelegramUpdateInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TelegramUpdateInboxRepository extends JpaRepository<TelegramUpdateInbox, Long> {

    List<TelegramUpdateInbox> findTop50ByProcessingStatusOrderByUpdateIdAsc(String processingStatus);

    @Query("SELECT u FROM TelegramUpdateInbox u WHERE u.processingStatus = 'PROCESSING' AND u.leaseExpiresAt < :now AND u.attemptCount < 5")
    List<TelegramUpdateInbox> findOrphanedLeasedUpdates(@Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TelegramUpdateInbox u SET u.processingStatus = 'DEAD_LETTER', u.lastError = 'Exhausted 5 lease attempts' WHERE u.processingStatus = 'PROCESSING' AND u.leaseExpiresAt < :now AND u.attemptCount >= 5")
    int markExhaustedLeasesDeadLetter(@Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TelegramUpdateInbox u SET u.processingStatus = 'PROCESSING', u.leaseExpiresAt = :leaseExpiresAt, u.attemptCount = u.attemptCount + 1 WHERE u.updateId = :updateId AND u.processingStatus = 'RECEIVED'")
    int claimLease(@Param("updateId") Long updateId, @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TelegramUpdateInbox u SET u.processingStatus = :status, u.processedAt = :processedAt, u.lastError = :lastError WHERE u.updateId = :updateId")
    int updateInboxStatus(@Param("updateId") Long updateId, @Param("status") String status, @Param("processedAt") LocalDateTime processedAt, @Param("lastError") String lastError);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM TelegramUpdateInbox u WHERE u.receivedAt < :cutoff AND u.processingStatus IN ('PROCESSED', 'FAILED', 'DEAD_LETTER')")
    int purgeOldUpdates(@Param("cutoff") LocalDateTime cutoff);
}
