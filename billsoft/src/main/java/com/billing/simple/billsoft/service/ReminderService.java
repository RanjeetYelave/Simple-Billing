package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Reminder;
import com.billing.simple.billsoft.repo.ReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReminderService {
    private final ReminderRepository reminderRepository;

    public ReminderService(ReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    public List<Reminder> getAll() {
        return reminderRepository.findAll();
    }

    public List<Reminder> getByFirm(Long firmId) {
        return reminderRepository.findByFirmId(firmId);
    }

    public List<Reminder> getActiveByFirm(Long firmId) {
        return reminderRepository.findByFirmIdAndCompletedFalse(firmId);
    }

    public Reminder create(Reminder reminder) {
        // Ensure createdAt is set via @PrePersist
        return reminderRepository.save(reminder);
    }

    @Transactional
    public Reminder markDone(Long id) {
        Reminder r = reminderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Reminder not found"));
        r.setCompleted(true);
        r.setCompletedAt(LocalDateTime.now());
        return r;
    }
}
