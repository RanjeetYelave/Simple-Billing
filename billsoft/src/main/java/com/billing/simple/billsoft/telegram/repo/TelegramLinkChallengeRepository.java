package com.billing.simple.billsoft.telegram.repo;

import com.billing.simple.billsoft.telegram.entities.TelegramLinkChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TelegramLinkChallengeRepository extends JpaRepository<TelegramLinkChallenge, Long> {

    Optional<TelegramLinkChallenge> findByChallengeHashAndStatus(String challengeHash, String status);

    @Modifying
    @Query("UPDATE TelegramLinkChallenge c SET c.status = 'USED', c.usedByUserId = :userId, c.usedAt = :now WHERE c.challengeHash = :hash AND c.status = 'PENDING' AND c.expiresAt > :now")
    int redeemChallenge(@Param("hash") String hash, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE TelegramLinkChallenge c SET c.failedAttempts = c.failedAttempts + 1 WHERE c.challengeHash = :hash")
    void incrementFailedAttempts(@Param("hash") String hash);

    @Modifying
    @Query("UPDATE TelegramLinkChallenge c SET c.status = 'EXPIRED' WHERE c.status = 'PENDING' AND c.expiresAt <= :now")
    int expirePendingChallenges(@Param("now") LocalDateTime now);
}
