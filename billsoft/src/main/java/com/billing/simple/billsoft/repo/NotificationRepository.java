package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.Notification;
import com.billing.simple.billsoft.entities.NotificationCategory;
import com.billing.simple.billsoft.entities.NotificationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByFirmIdAndEventKey(Long firmId, String eventKey);

    Optional<Notification> findByIdAndFirmId(Long id, Long firmId);

    List<Notification> findByFirmIdAndStatusInOrderByCreatedAtDesc(Long firmId, Collection<NotificationStatus> statuses);

    List<Notification> findByFirmIdAndStatusInOrderByCreatedAtDesc(Long firmId, Collection<NotificationStatus> statuses, Pageable pageable);

    List<Notification> findByFirmIdInAndStatusInOrderByCreatedAtDesc(Collection<Long> firmIds, Collection<NotificationStatus> statuses, Pageable pageable);

    List<Notification> findByFirmIdInAndStatusInOrderByCreatedAtDesc(Collection<Long> firmIds, Collection<NotificationStatus> statuses);

    List<Notification> findByFirmIdAndCategoryAndStatusInOrderByCreatedAtDesc(Long firmId, NotificationCategory category, Collection<NotificationStatus> statuses);

    List<Notification> findByFirmIdInAndCategoryAndStatusInOrderByCreatedAtDesc(Collection<Long> firmIds, NotificationCategory category, Collection<NotificationStatus> statuses, Pageable pageable);

    long countByFirmIdAndStatus(Long firmId, NotificationStatus status);

    long countByFirmIdAndStatusIn(Long firmId, Collection<NotificationStatus> statuses);

    List<Notification> findByStatusAndSnoozedUntilLessThanEqual(NotificationStatus status, LocalDateTime now);

    List<Notification> findByStatusInAndExpiresAtLessThanEqual(Collection<NotificationStatus> statuses, LocalDateTime now);

    @Modifying
    @Query("UPDATE Notification n SET n.status = :targetStatus, n.updatedAt = :now WHERE n.firmId = :firmId AND n.status = :currentStatus")
    int updateStatusForFirm(@Param("firmId") Long firmId,
                            @Param("currentStatus") NotificationStatus currentStatus,
                            @Param("targetStatus") NotificationStatus targetStatus,
                            @Param("now") LocalDateTime now);

    boolean existsByFirmIdAndEventKey(Long firmId, String eventKey);
}
