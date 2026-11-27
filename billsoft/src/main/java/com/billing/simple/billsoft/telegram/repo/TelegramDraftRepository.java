package com.billing.simple.billsoft.telegram.repo;

import com.billing.simple.billsoft.telegram.entities.TelegramDraft;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TelegramDraftRepository extends JpaRepository<TelegramDraft, Long> {

    Optional<TelegramDraft> findByChatIdAndDraftStateNot(Long chatId, String draftState);

    Optional<TelegramDraft> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM TelegramDraft d WHERE d.draftId = :draftId")
    Optional<TelegramDraft> findByIdForUpdate(@Param("draftId") Long draftId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TelegramDraft d SET d.draftState = 'CANCELLED' WHERE d.userId = :userId AND d.draftState IN ('BUILDING', 'PREVIEW')")
    int cancelActiveDraftsForUser(@Param("userId") Long userId);
}
