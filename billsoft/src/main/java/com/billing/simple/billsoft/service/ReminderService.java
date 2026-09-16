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

    private final com.billing.simple.billsoft.repo.CustomerRepository customerRepository;

    public ReminderService(ReminderRepository reminderRepository, com.billing.simple.billsoft.repo.CustomerRepository customerRepository) {
        this.reminderRepository = reminderRepository;
        this.customerRepository = customerRepository;
    }

    public List<Reminder> getAll() {
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (firmId != null) {
            return reminderRepository.findByFirmId(firmId);
        }
        return reminderRepository.findAll();
    }

    public List<Reminder> getByFirm(Long firmId) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm access prohibited");
        }
        return reminderRepository.findByFirmId(authoritativeFirmId != null ? authoritativeFirmId : firmId);
    }

    public List<Reminder> getActiveByFirm(Long firmId) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm access prohibited");
        }
        return reminderRepository.findByFirmIdAndCompletedFalse(authoritativeFirmId != null ? authoritativeFirmId : firmId);
    }

    public Reminder create(Reminder reminder) {
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (firmId != null) {
            reminder.setFirmId(firmId);
        }
        if (reminder.getCustomerId() != null && reminder.getFirmId() != null) {
            if (!customerRepository.existsByIdAndFirmId(reminder.getCustomerId(), reminder.getFirmId())) {
                throw new com.billing.simple.billsoft.security.TenantSecurityException("Referenced customer does not belong to the current firm");
            }
        }
        return reminderRepository.save(reminder);
    }

    @Transactional
    public Reminder update(Long id, Reminder updated) {
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        Reminder r = null;
        if (firmId != null) {
            r = reminderRepository.findByIdAndFirmId(id, firmId).orElse(null);
            if (r == null) {
                Reminder fallback = reminderRepository.findById(id).orElse(null);
                if (fallback != null && (fallback.getFirmId() == null || fallback.getFirmId().equals(firmId))) {
                    r = fallback;
                    r.setFirmId(firmId);
                }
            }
        } else {
            r = reminderRepository.findById(id).orElse(null);
        }
        if (r == null) {
            throw new IllegalArgumentException("Reminder not found");
        }

        if (updated.getCustomerId() != null && r.getFirmId() != null) {
            if (!customerRepository.existsByIdAndFirmId(updated.getCustomerId(), r.getFirmId())) {
                throw new com.billing.simple.billsoft.security.TenantSecurityException("Referenced customer does not belong to the current firm");
            }
            r.setCustomerId(updated.getCustomerId());
        } else if (updated.getCustomerId() == null) {
            r.setCustomerId(null);
        }

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
        // Do NOT allow changing firmId
        if (updated.isCompleted() && r.getCompletedAt() == null) {
            r.setCompletedAt(LocalDateTime.now());
        } else if (!updated.isCompleted()) {
            r.setCompletedAt(null);
        }
        return reminderRepository.save(r);
    }

    @Transactional
    public boolean delete(Long id) {
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (firmId != null) {
            if (reminderRepository.existsByIdAndFirmId(id, firmId)) {
                reminderRepository.deleteByIdAndFirmId(id, firmId);
                return true;
            }
            if (reminderRepository.existsById(id)) {
                reminderRepository.deleteById(id);
                return true;
            }
            return false;
        }
        if (!reminderRepository.existsById(id)) return false;
        reminderRepository.deleteById(id);
        return true;
    }

    @Transactional
    public Reminder markDone(Long id) {
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        Reminder r = null;
        if (firmId != null) {
            r = reminderRepository.findByIdAndFirmId(id, firmId).orElse(null);
            if (r == null) {
                Reminder fallback = reminderRepository.findById(id).orElse(null);
                if (fallback != null && (fallback.getFirmId() == null || fallback.getFirmId().equals(firmId))) {
                    r = fallback;
                    r.setFirmId(firmId);
                }
            }
        } else {
            r = reminderRepository.findById(id).orElse(null);
        }
        if (r == null) {
            throw new IllegalArgumentException("Reminder not found");
        }
        r.setCompleted(true);
        r.setCompletedAt(LocalDateTime.now());
        r.setStatus("DONE");
        r.setProgress(100);
        return reminderRepository.save(r);
    }
}
