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
    public Reminder update(Long id, Reminder updated) {
        Reminder r = reminderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Reminder not found"));
        r.setTitle(updated.getTitle());
        r.setNote(updated.getNote());
        r.setDueDate(updated.getDueDate());
        if (updated.getDueDate() != null && !updated.getDueDate().equals(r.getDueDate())) {
            r.setInboxNotified(false);
        }
        if (!updated.isCompleted() && r.isCompleted()) {
            r.setInboxNotified(false);
        }
        r.setCompleted(updated.isCompleted());
        r.setType(updated.getType());
        r.setTags(updated.getTags());
        r.setStatus(updated.getStatus());
        r.setProgress(updated.getProgress());
        r.setCustomerId(updated.getCustomerId());
        r.setFirmId(updated.getFirmId());
        if (updated.isCompleted() && r.getCompletedAt() == null) {
            r.setCompletedAt(LocalDateTime.now());
        } else if (!updated.isCompleted()) {
            r.setCompletedAt(null);
        }
        return reminderRepository.save(r);
    }

    @Transactional
    public boolean delete(Long id) {
        if (!reminderRepository.existsById(id)) return false;
        reminderRepository.deleteById(id);
        return true;
    }

    @Transactional
    public Reminder markDone(Long id) {
        Reminder r = reminderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Reminder not found"));
        r.setCompleted(true);
        r.setCompletedAt(LocalDateTime.now());
        r.setStatus("DONE");
        r.setProgress(100);
        return r;
    }
}
