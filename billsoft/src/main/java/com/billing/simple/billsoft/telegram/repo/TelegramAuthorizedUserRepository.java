package com.billing.simple.billsoft.telegram.repo;

import com.billing.simple.billsoft.telegram.entities.TelegramAuthorizedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramAuthorizedUserRepository extends JpaRepository<TelegramAuthorizedUser, Long> {

    Optional<TelegramAuthorizedUser> findByUserIdAndStatus(Long userId, String status);

    List<TelegramAuthorizedUser> findAllByOrderByAuthorizedAtDesc();

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TelegramAuthorizedUser u SET u.status = :status WHERE u.userId = :userId")
    int updateStatus(@Param("userId") Long userId, @Param("status") String status);
}
