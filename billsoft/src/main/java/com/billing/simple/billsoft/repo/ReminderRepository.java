package com.billing.simple.billsoft.repo;

import com.billing.simple.billsoft.entities.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {
    List<Reminder> findByFirmId(Long firmId);
    List<Reminder> findByFirmIdAndCompletedFalse(Long firmId);
    List<Reminder> findByCompletedTrue();
}
