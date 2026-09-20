package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.Reminder;
import com.billing.simple.billsoft.repo.ReminderRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReminderService {
    private final ReminderRepository reminderRepository;

    private final com.billing.simple.billsoft.repo.CustomerRepository customerRepository;

    public ReminderService(ReminderRepository reminderRepository,
            com.billing.simple.billsoft.repo.CustomerRepository customerRepository) {
        this.reminderRepository = reminderRepository;
        this.customerRepository = customerRepository;
    }

    private Long resolveFirmId(Long explicitFirmId) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && explicitFirmId != null && !authoritativeFirmId.equals(explicitFirmId)) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }
        Long target = authoritativeFirmId != null ? authoritativeFirmId : explicitFirmId;
        if (target == null || target <= 0) {
            throw new TenantSecurityException("Active firm context is required");
        }
        return target;
    }

    public List<Reminder> getAll() {
        Long firmId = TenantContext.getCurrentFirmId();
        return firmId != null ? reminderRepository.findByFirmId(firmId) : reminderRepository.findAll();
    }

    public List<Reminder> getByFirm(Long firmId) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && firmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }
        Long target = authoritativeFirmId != null ? authoritativeFirmId : firmId;
        return target != null ? reminderRepository.findByFirmId(target) : reminderRepository.findAll();
    }

    public List<Reminder> getActiveByFirm(Long firmId) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && firmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new TenantSecurityException("Cross-firm access prohibited");
        }
        Long target = authoritativeFirmId != null ? authoritativeFirmId : firmId;
        return target != null ? reminderRepository.findByFirmIdAndCompletedFalse(target) : reminderRepository.findByCompletedFalse();
    }

    public Reminder create(Reminder reminder) {
        Long firmId = reminder.getFirmId() != null ? reminder.getFirmId() : TenantContext.getCurrentFirmId();
        if (firmId != null) {
            reminder.setFirmId(firmId);
            if (reminder.getCustomerId() != null) {
                if (!customerRepository.existsByIdAndFirmId(reminder.getCustomerId(), firmId)) {
                    throw new TenantSecurityException(
                            "Referenced customer does not belong to the current firm");
                }
            }
        }
        return reminderRepository.save(reminder);
    }

    @Transactional
    public Reminder update(Long id, Reminder updated) {
        Long firmId = TenantContext.getCurrentFirmId();
        Reminder r = (firmId != null ? reminderRepository.findByIdAndFirmId(id, firmId) : reminderRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Reminder not found"));

        if (updated.getCustomerId() != null) {
            Long targetFid = r.getFirmId() != null ? r.getFirmId() : firmId;
            if (targetFid != null && !customerRepository.existsByIdAndFirmId(updated.getCustomerId(), targetFid)) {
                throw new TenantSecurityException(
                        "Referenced customer does not belong to the current firm");
            }
            r.setCustomerId(updated.getCustomerId());
        } else {
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
        Long firmId = TenantContext.getCurrentFirmId();
        if (firmId != null) {
            if (!reminderRepository.existsByIdAndFirmId(id, firmId))
                return false;
            reminderRepository.deleteByIdAndFirmId(id, firmId);
            return true;
        } else {
            if (!reminderRepository.existsById(id))
                return false;
            reminderRepository.deleteById(id);
            return true;
        }
    }

    @Transactional
    public Reminder markDone(Long id) {
        Long firmId = TenantContext.getCurrentFirmId();
        Reminder r = (firmId != null ? reminderRepository.findByIdAndFirmId(id, firmId) : reminderRepository.findById(id))
                .orElseThrow(() -> new IllegalArgumentException("Reminder not found"));
        r.setCompleted(true);
        r.setCompletedAt(LocalDateTime.now());
        r.setStatus("DONE");
        r.setProgress(100);
        return reminderRepository.save(r);
    }
}
