package com.billing.simple.billsoft.telegram.repo;

import com.billing.simple.billsoft.telegram.entities.TelegramUserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TelegramUserSessionRepository extends JpaRepository<TelegramUserSession, Long> {

    Optional<TelegramUserSession> findByChatId(Long chatId);

    @Modifying
    @Query("DELETE FROM TelegramUserSession s WHERE s.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
