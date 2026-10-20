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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private NotificationService notificationService;

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
        return target;
    }

    public List<Reminder> getAll() {
        Long firmId = TenantContext.getCurrentFirmId();
        return firmId != null ? reminderRepository.findByFirmId(firmId) : reminderRepository.findAll();
    }

    public List<Reminder> getByFirm(Long firmId) {
        Long authoritativeFirmId = resolveFirmId(firmId);
        return authoritativeFirmId != null ? reminderRepository.findByFirmId(authoritativeFirmId) : java.util.Collections.emptyList();
    }

    public List<Reminder> getActiveByFirm(Long firmId) {
        Long authoritativeFirmId = resolveFirmId(firmId);
        return authoritativeFirmId != null ? reminderRepository.findByFirmIdAndCompletedFalse(authoritativeFirmId) : java.util.Collections.emptyList();
    }

    public Reminder create(Reminder reminder) {
        Long authoritativeFirmId = TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null) {
            if (reminder.getFirmId() != null && !authoritativeFirmId.equals(reminder.getFirmId())) {
                throw new TenantSecurityException("Cross-firm reminder creation prohibited");
            }
            reminder.setFirmId(authoritativeFirmId);
        }

        if (reminder.getCustomerId() != null && reminder.getFirmId() != null) {
            if (!customerRepository.existsByIdAndFirmId(reminder.getCustomerId(), reminder.getFirmId())) {
                throw new TenantSecurityException("Referenced customer does not belong to the current firm");
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
        java.util.Optional<Reminder> existing = (firmId != null ? reminderRepository.findByIdAndFirmId(id, firmId) : reminderRepository.findById(id));
        if (existing.isEmpty()) {
            return false;
        }
        Reminder r = existing.get();
        if (firmId != null) {
            reminderRepository.deleteByIdAndFirmId(id, firmId);
        } else {
            reminderRepository.deleteById(id);
        }
        if (notificationService != null && r.getId() != null) {
            String typeStr = (r.getType() != null && "task".equalsIgnoreCase(r.getType())) ? "task" : "reminder";
            notificationService.resolveByEventKey(firmId, "planner:" + typeStr + ":" + r.getId(), com.billing.simple.billsoft.entities.NotificationStatus.DISMISSED);
        }
        return true;
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
        Reminder saved = reminderRepository.save(r);
        if (notificationService != null && saved.getId() != null) {
            String typeStr = (saved.getType() != null && "task".equalsIgnoreCase(saved.getType())) ? "task" : "reminder";
            Long notifFirmId = saved.getFirmId() != null ? saved.getFirmId() : firmId;
            if (notifFirmId != null) {
                notificationService.resolveByEventKey(notifFirmId, "planner:" + typeStr + ":" + saved.getId(), com.billing.simple.billsoft.entities.NotificationStatus.ACTIONED);
            }
        }
        return saved;
    }
}
