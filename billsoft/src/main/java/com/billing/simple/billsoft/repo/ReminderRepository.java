package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByFirmId(Long firmId);

    List<Reminder> findByFirmIdAndCompletedFalse(Long firmId);

    List<Reminder> findByCompletedFalse();

    List<Reminder> findByCompletedTrue();

    Optional<Reminder> findByIdAndFirmId(Long id, Long firmId);

    boolean existsByIdAndFirmId(Long id, Long firmId);

    void deleteByIdAndFirmId(Long id, Long firmId);

    long countByFirmId(Long firmId);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Reminder r WHERE r.completed = false AND r.inboxNotified = false AND r.dueDate IS NOT NULL AND r.dueDate <= :now")
    List<Reminder> findDueReminders(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
}

