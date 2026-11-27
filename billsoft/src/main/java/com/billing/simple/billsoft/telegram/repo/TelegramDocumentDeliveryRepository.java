package com.billing.simple.billsoft.telegram.repo;

import com.billing.simple.billsoft.telegram.entities.TelegramDocumentDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelegramDocumentDeliveryRepository extends JpaRepository<TelegramDocumentDelivery, Long> {

    List<TelegramDocumentDelivery> findTop20ByDeliveryStateOrderByCreatedAtAsc(String deliveryState);

    List<TelegramDocumentDelivery> findByChatIdOrderByCreatedAtDesc(Long chatId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE TelegramDocumentDelivery d SET d.deliveryState = 'CANCELLED', d.lastError = 'Account revoked' WHERE d.userId = :userId AND d.deliveryState IN ('QUEUED', 'SENDING')")
    int cancelPendingDeliveriesForUser(@Param("userId") Long userId);
}
