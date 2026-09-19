package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.NotificationActionRequest;
import com.billing.simple.billsoft.dto.NotificationPreferencesDto;
import com.billing.simple.billsoft.dto.NotificationRequest;
import com.billing.simple.billsoft.dto.NotificationSummaryResponse;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.licensing.LicenseStorage;
import com.billing.simple.billsoft.licensing.LicensingConfig;
import com.billing.simple.billsoft.licensing.MachineIdentity;
import com.billing.simple.billsoft.licensing.model.CustomerMessage;
import com.billing.simple.billsoft.repo.InboxMessageRepository;
import com.billing.simple.billsoft.repo.NotificationPreferenceRepository;
import com.billing.simple.billsoft.repo.NotificationRepository;
import com.billing.simple.billsoft.security.TenantContext;
import com.billing.simple.billsoft.security.TenantSecurityException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final InboxMessageRepository legacyInboxRepository;

    @Autowired(required = false)
    @Lazy
    private ReminderService reminderService;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationPreferenceRepository preferenceRepository,
                               InboxMessageRepository legacyInboxRepository) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.legacyInboxRepository = legacyInboxRepository;
    }

    @PostConstruct
    public void init() {
        try {
            migrateLegacyData();
        } catch (Exception e) {
            log.warn("Legacy notification migration warning: {}", e.getMessage());
        }
    }

    /**
     * Resolves the authoritative firm ID for multi-tenant isolation.
     */
    public Long resolveAuthoritativeFirmId(Long requestedFirmId) {
        Long tenantFirmId = TenantContext.getCurrentFirmId();
        if (tenantFirmId != null) {
            if (requestedFirmId != null && !requestedFirmId.equals(tenantFirmId) && !Notification.GLOBAL_FIRM_ID.equals(requestedFirmId)) {
                throw new TenantSecurityException("Cross-firm notification access prohibited");
            }
            return tenantFirmId;
        }
        return requestedFirmId != null ? requestedFirmId : Notification.GLOBAL_FIRM_ID;
    }

    /**
     * Verifies that the notification belongs to the current tenant.
     */
    private void verifyOwnership(Notification notification, Long authoritativeFirmId) {
        if (notification == null) {
            throw new IllegalArgumentException("Notification not found");
        }
        if (authoritativeFirmId == null || Notification.GLOBAL_FIRM_ID.equals(authoritativeFirmId)) {
            return;
        }
        if (!Notification.GLOBAL_FIRM_ID.equals(notification.getFirmId())
                && !authoritativeFirmId.equals(notification.getFirmId())) {
            throw new TenantSecurityException("Access to foreign notification is prohibited");
        }
    }

    /**
     * Upserts a notification with deterministic deduplication by (firmId, eventKey).
     */
    @Transactional
    public Notification createOrUpdate(NotificationRequest req) {
        if (req == null || req.getEventKey() == null || req.getEventKey().isBlank()) {
            throw new IllegalArgumentException("Event key is required for notification deduplication");
        }

        Long firmId = req.getFirmId() != null ? req.getFirmId() : resolveAuthoritativeFirmId(null);
        if (firmId == null) {
            firmId = Notification.GLOBAL_FIRM_ID;
        }

        // Check if firm has disabled this notification category
        NotificationCategory cat = req.getCategory() != null ? req.getCategory() : NotificationCategory.SYSTEM;
        if (!isCategoryAllowedForFirm(firmId, cat)) {
            log.debug("Notification suppressed by firm preference: firmId={}, category={}", firmId, cat);
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<Notification> existingOpt = notificationRepository.findByFirmIdAndEventKey(firmId, req.getEventKey());

        if (existingOpt.isEmpty()) {
            Notification n = Notification.builder()
                    .firmId(firmId)
                    .eventKey(req.getEventKey().trim())
                    .category(cat)
                    .priority(req.getPriority() != null ? req.getPriority() : NotificationPriority.NORMAL)
                    .title(req.getTitle() != null ? req.getTitle() : "Notification")
                    .body(req.getBody())
                    .sender(req.getSender() != null ? req.getSender() : "System")
                    .status(NotificationStatus.UNREAD)
                    .primaryActionLabel(req.getPrimaryActionLabel())
                    .primaryActionType(req.getPrimaryActionType())
                    .primaryActionTarget(req.getPrimaryActionTarget())
                    .secondaryActionLabel(req.getSecondaryActionLabel())
                    .secondaryActionType(req.getSecondaryActionType())
                    .secondaryActionTarget(req.getSecondaryActionTarget())
                    .expiresAt(req.getExpiresAt())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            return notificationRepository.save(n);
        }

        Notification existing = existingOpt.get();

        // If dismissed, actioned, or expired: DO NOT resurrect on routine producer checks
        if (existing.getStatus() == NotificationStatus.DISMISSED
                || existing.getStatus() == NotificationStatus.ACTIONED
                || existing.getStatus() == NotificationStatus.EXPIRED) {
            return existing;
        }

        // If snoozed: check if snooze duration expired
        if (existing.getStatus() == NotificationStatus.SNOOZED) {
            if (existing.getSnoozedUntil() != null && now.isAfter(existing.getSnoozedUntil())) {
                existing.setStatus(NotificationStatus.UNREAD);
                existing.setSnoozedUntil(null);
            }
        }

        // Update content if changed
        if (req.getTitle() != null && !req.getTitle().equals(existing.getTitle())) {
            existing.setTitle(req.getTitle());
        }
        if (req.getBody() != null && !req.getBody().equals(existing.getBody())) {
            existing.setBody(req.getBody());
        }
        if (req.getPriority() != null) {
            existing.setPriority(req.getPriority());
        }
        if (req.getPrimaryActionLabel() != null) {
            existing.setPrimaryActionLabel(req.getPrimaryActionLabel());
            existing.setPrimaryActionType(req.getPrimaryActionType());
            existing.setPrimaryActionTarget(req.getPrimaryActionTarget());
        }
        if (req.getSecondaryActionLabel() != null) {
            existing.setSecondaryActionLabel(req.getSecondaryActionLabel());
            existing.setSecondaryActionType(req.getSecondaryActionType());
            existing.setSecondaryActionTarget(req.getSecondaryActionTarget());
        }
        if (req.getExpiresAt() != null) {
            existing.setExpiresAt(req.getExpiresAt());
        }
        existing.setUpdatedAt(now);

        return notificationRepository.save(existing);
    }

    /**
     * Reconciles expired snoozes and natural expirations before any read operation.
     */
    @Transactional
    public void reconcileExpiredSnoozes() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Wake up expired snoozes
        List<Notification> expiredSnoozes = notificationRepository.findByStatusAndSnoozedUntilLessThanEqual(NotificationStatus.SNOOZED, now);
        for (Notification n : expiredSnoozes) {
            n.setStatus(NotificationStatus.UNREAD);
            n.setSnoozedUntil(null);
            n.setUpdatedAt(now);
            notificationRepository.save(n);
        }

        // 2. Mark natural expirations
        List<Notification> naturalExpirations = notificationRepository.findByStatusInAndExpiresAtLessThanEqual(
                List.of(NotificationStatus.UNREAD, NotificationStatus.READ, NotificationStatus.SNOOZED),
                now
        );
        for (Notification n : naturalExpirations) {
            n.setStatus(NotificationStatus.EXPIRED);
            n.setUpdatedAt(now);
            notificationRepository.save(n);
        }
    }

    /**
     * Returns lightweight summary for the Topbar Bell icon and drawer.
     */
    @Transactional
    public NotificationSummaryResponse getSummary(Long requestedFirmId) {
        reconcileExpiredSnoozes();
        Long firmId = resolveAuthoritativeFirmId(requestedFirmId);

        NotificationPreference pref = getOrCreatePreference(firmId);
        if (!pref.isEnabled() || !pref.isBellEnabled()) {
            return NotificationSummaryResponse.builder()
                    .unreadCount(0)
                    .totalActiveCount(0)
                    .items(Collections.emptyList())
                    .bellEnabled(false)
                    .inboxEnabled(pref.isInboxEnabled())
                    .build();
        }

        long unreadCount = notificationRepository.countByFirmIdAndStatus(firmId, NotificationStatus.UNREAD);
        if (!Notification.GLOBAL_FIRM_ID.equals(firmId)) {
            unreadCount += notificationRepository.countByFirmIdAndStatus(Notification.GLOBAL_FIRM_ID, NotificationStatus.UNREAD);
        }

        long activeCount = notificationRepository.countByFirmIdAndStatusIn(firmId, List.of(NotificationStatus.UNREAD, NotificationStatus.READ));
        if (!Notification.GLOBAL_FIRM_ID.equals(firmId)) {
            activeCount += notificationRepository.countByFirmIdAndStatusIn(Notification.GLOBAL_FIRM_ID, List.of(NotificationStatus.UNREAD, NotificationStatus.READ));
        }

        List<Long> targetFirmIds = Notification.GLOBAL_FIRM_ID.equals(firmId)
                ? List.of(Notification.GLOBAL_FIRM_ID)
                : List.of(firmId, Notification.GLOBAL_FIRM_ID);

        List<Notification> activeItems = notificationRepository.findByFirmIdInAndStatusInOrderByCreatedAtDesc(
                targetFirmIds,
                List.of(NotificationStatus.UNREAD, NotificationStatus.READ),
                PageRequest.of(0, 10)
        );

        return NotificationSummaryResponse.builder()
                .unreadCount(unreadCount)
                .totalActiveCount(activeCount)
                .items(activeItems)
                .bellEnabled(pref.isBellEnabled())
                .inboxEnabled(pref.isInboxEnabled())
                .build();
    }

    /**
     * Lists notifications with multi-status, category, and limit filters.
     */
    @Transactional
    public List<Notification> listNotifications(Long requestedFirmId, String statusFilter, String categoryFilter, int limit) {
        reconcileExpiredSnoozes();
        Long firmId = resolveAuthoritativeFirmId(requestedFirmId);

        Collection<NotificationStatus> statuses;
        String normalizedStatus = statusFilter != null ? statusFilter.trim().toUpperCase() : "ACTIVE";

        switch (normalizedStatus) {
            case "UNREAD":
                statuses = List.of(NotificationStatus.UNREAD);
                break;
            case "READ":
                statuses = List.of(NotificationStatus.READ);
                break;
            case "SNOOZED":
                statuses = List.of(NotificationStatus.SNOOZED);
                break;
            case "DISMISSED":
                statuses = List.of(NotificationStatus.DISMISSED);
                break;
            case "ACTIONED":
                statuses = List.of(NotificationStatus.ACTIONED);
                break;
            case "ARCHIVED":
                statuses = List.of(NotificationStatus.DISMISSED, NotificationStatus.ACTIONED, NotificationStatus.EXPIRED);
                break;
            case "ALL":
                statuses = Arrays.asList(NotificationStatus.values());
                break;
            case "ACTIVE":
            default:
                statuses = List.of(NotificationStatus.UNREAD, NotificationStatus.READ, NotificationStatus.SNOOZED);
                break;
        }

        int maxLimit = (limit > 0 && limit <= 200) ? limit : 100;
        List<Long> targetFirmIds = Notification.GLOBAL_FIRM_ID.equals(firmId)
                ? List.of(Notification.GLOBAL_FIRM_ID)
                : List.of(firmId, Notification.GLOBAL_FIRM_ID);

        if (categoryFilter != null && !categoryFilter.isBlank() && !"ALL".equalsIgnoreCase(categoryFilter)) {
            try {
                NotificationCategory cat = NotificationCategory.valueOf(categoryFilter.trim().toUpperCase());
                return notificationRepository.findByFirmIdInAndCategoryAndStatusInOrderByCreatedAtDesc(targetFirmIds, cat, statuses, PageRequest.of(0, maxLimit));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return notificationRepository.findByFirmIdInAndStatusInOrderByCreatedAtDesc(
                targetFirmIds,
                statuses,
                PageRequest.of(0, maxLimit)
        );
    }

    @Transactional
    public Notification markRead(Long id) {
        Long firmId = resolveAuthoritativeFirmId(null);
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        verifyOwnership(n, firmId);

        n.setStatus(NotificationStatus.READ);
        n.setUpdatedAt(LocalDateTime.now());
        return notificationRepository.save(n);
    }

    @Transactional
    public int markAllRead(Long requestedFirmId) {
        Long firmId = resolveAuthoritativeFirmId(requestedFirmId);
        LocalDateTime now = LocalDateTime.now();
        return notificationRepository.updateStatusForFirm(firmId, NotificationStatus.UNREAD, NotificationStatus.READ, now);
    }

    @Transactional
    public Notification snooze(Long id, String durationCode) {
        Long firmId = resolveAuthoritativeFirmId(null);
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        verifyOwnership(n, firmId);

        Duration duration = parseDuration(durationCode);
        LocalDateTime target = LocalDateTime.now().plus(duration);

        n.setStatus(NotificationStatus.SNOOZED);
        n.setSnoozedUntil(target);
        n.setUpdatedAt(LocalDateTime.now());
        return notificationRepository.save(n);
    }

    @Transactional
    public Notification dismiss(Long id) {
        Long firmId = resolveAuthoritativeFirmId(null);
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        verifyOwnership(n, firmId);

        n.setStatus(NotificationStatus.DISMISSED);
        n.setUpdatedAt(LocalDateTime.now());
        return notificationRepository.save(n);
    }

    @Transactional
    public Notification executeAction(Long id, NotificationActionRequest request) {
        Long firmId = resolveAuthoritativeFirmId(null);
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        verifyOwnership(n, firmId);

        String choice = (request != null && request.getActionChoice() != null)
                ? request.getActionChoice().trim().toUpperCase()
                : "PRIMARY";

        NotificationActionType actionType;
        String actionTarget;

        if ("SECONDARY".equals(choice)) {
            if (n.getSecondaryActionType() == null) {
                throw new IllegalArgumentException("Notification has no secondary action configured");
            }
            actionType = n.getSecondaryActionType();
            actionTarget = n.getSecondaryActionTarget();
        } else {
            if (n.getPrimaryActionType() == null) {
                throw new IllegalArgumentException("Notification has no primary action configured");
            }
            actionType = n.getPrimaryActionType();
            actionTarget = n.getPrimaryActionTarget();
        }

        // Handle backend-approved API_ACTION handlers
        if (actionType == NotificationActionType.API_ACTION && actionTarget != null) {
            handleBackendApprovedApiAction(n, actionTarget, request.getPayload());
        }

        n.setStatus(NotificationStatus.ACTIONED);
        n.setUpdatedAt(LocalDateTime.now());
        return notificationRepository.save(n);
    }

    private void handleBackendApprovedApiAction(Notification n, String target, String payload) {
        if ("MARK_TASK_DONE".equalsIgnoreCase(target) || "COMPLETE_REMINDER".equalsIgnoreCase(target)) {
            // Extract reminderId if eventKey is planner:reminder:{id} or planner:task:{id}
            if (n.getEventKey() != null && (n.getEventKey().startsWith("planner:reminder:") || n.getEventKey().startsWith("planner:task:"))) {
                String[] parts = n.getEventKey().split(":");
                if (parts.length >= 3) {
                    try {
                        Long reminderId = Long.parseLong(parts[2]);
                        if (reminderService != null) {
                            reminderService.markDone(reminderId);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to mark linked reminder done: {}", e.getMessage());
                    }
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesDto getPreferences(Long requestedFirmId) {
        Long firmId = resolveAuthoritativeFirmId(requestedFirmId);
        NotificationPreference pref = getOrCreatePreference(firmId);
        return NotificationPreferencesDto.builder()
                .enabled(pref.isEnabled())
                .bellEnabled(pref.isBellEnabled())
                .inboxEnabled(pref.isInboxEnabled())
                .defaultSnooze(pref.getDefaultSnooze())
                .licensingEnabled(pref.isLicensingEnabled())
                .billingEnabled(pref.isBillingEnabled())
                .inventoryEnabled(pref.isInventoryEnabled())
                .purchaseEnabled(pref.isPurchaseEnabled())
                .plannerEnabled(pref.isPlannerEnabled())
                .hrEnabled(pref.isHrEnabled())
                .systemEnabled(pref.isSystemEnabled())
                .build();
    }

    @Transactional
    public NotificationPreferencesDto updatePreferences(Long requestedFirmId, NotificationPreferencesDto dto) {
        Long firmId = resolveAuthoritativeFirmId(requestedFirmId);
        NotificationPreference pref = getOrCreatePreference(firmId);

        if (dto != null) {
            pref.setEnabled(dto.isEnabled());
            pref.setBellEnabled(dto.isBellEnabled());
            pref.setInboxEnabled(dto.isInboxEnabled());
            if (dto.getDefaultSnooze() != null && !dto.getDefaultSnooze().isBlank()) {
                pref.setDefaultSnooze(dto.getDefaultSnooze().trim());
            }
            pref.setLicensingEnabled(dto.isLicensingEnabled());
            pref.setBillingEnabled(dto.isBillingEnabled());
            pref.setInventoryEnabled(dto.isInventoryEnabled());
            pref.setPurchaseEnabled(dto.isPurchaseEnabled());
            pref.setPlannerEnabled(dto.isPlannerEnabled());
            pref.setHrEnabled(dto.isHrEnabled());
            pref.setSystemEnabled(dto.isSystemEnabled());
            pref.setUpdatedAt(LocalDateTime.now());
            preferenceRepository.save(pref);
        }

        return getPreferences(firmId);
    }

    private NotificationPreference getOrCreatePreference(Long firmId) {
        return preferenceRepository.findByFirmId(firmId).orElseGet(() -> {
            NotificationPreference defaultPref = NotificationPreference.builder()
                    .firmId(firmId)
                    .enabled(true)
                    .bellEnabled(true)
                    .inboxEnabled(true)
                    .defaultSnooze("1d")
                    .licensingEnabled(true)
                    .billingEnabled(true)
                    .inventoryEnabled(true)
                    .purchaseEnabled(true)
                    .plannerEnabled(true)
                    .hrEnabled(true)
                    .systemEnabled(true)
                    .updatedAt(LocalDateTime.now())
                    .build();
            try {
                return preferenceRepository.save(defaultPref);
            } catch (Exception e) {
                return defaultPref;
            }
        });
    }

    private boolean isCategoryAllowedForFirm(Long firmId, NotificationCategory category) {
        try {
            NotificationPreference pref = getOrCreatePreference(firmId);
            return pref.isCategoryEnabled(category);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Idempotent migration of legacy inbox_messages table and disk inbox_messages.json.
     */
    @Transactional
    public void migrateLegacyData() {
        // 1. Migrate database table inbox_messages
        if (legacyInboxRepository != null) {
            List<InboxMessage> legacyList = legacyInboxRepository.findAll();
            for (InboxMessage msg : legacyList) {
                if (msg.getId() == null) continue;
                String eventKey;
                NotificationCategory cat;
                if (msg.getReminderId() != null) {
                    eventKey = "planner:reminder:" + msg.getReminderId();
                    cat = NotificationCategory.PLANNER;
                } else if (msg.getSubject() != null && msg.getSubject().contains("Inventory Alert")) {
                    eventKey = "inventory:low-stock:legacy:" + msg.getId();
                    cat = NotificationCategory.INVENTORY;
                } else if (msg.getSubject() != null && msg.getSubject().contains("Overdue Invoice")) {
                    eventKey = "billing:invoice:overdue:legacy:" + msg.getId();
                    cat = NotificationCategory.BILLING;
                } else if (msg.getSubject() != null && msg.getSubject().contains("Expected Delivery")) {
                    eventKey = "purchase:po:delivery:legacy:" + msg.getId();
                    cat = NotificationCategory.PURCHASE;
                } else if (msg.getSubject() != null && msg.getSubject().contains("Payroll Reminder")) {
                    eventKey = "hr:payroll:legacy:" + msg.getId();
                    cat = NotificationCategory.HR;
                } else {
                    eventKey = "legacy:inbox:" + (msg.getFirmId() != null ? msg.getFirmId() : "0") + ":" + msg.getId();
                    cat = NotificationCategory.SYSTEM;
                }

                Long firmId = msg.getFirmId() != null ? msg.getFirmId() : Notification.GLOBAL_FIRM_ID;
                if (!notificationRepository.existsByFirmIdAndEventKey(firmId, eventKey)) {
                    Notification n = Notification.builder()
                            .firmId(firmId)
                            .eventKey(eventKey)
                            .category(cat)
                            .priority(NotificationPriority.NORMAL)
                            .title(msg.getSubject() != null ? msg.getSubject() : "Message")
                            .body(msg.getBody())
                            .sender(msg.getSender() != null ? msg.getSender() : "System")
                            .status(msg.isRead() ? NotificationStatus.READ : NotificationStatus.UNREAD)
                            .createdAt(msg.getCreatedAt() != null ? msg.getCreatedAt() : LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    notificationRepository.save(n);
                }
            }
        }

        // 2. Migrate disk inbox_messages.json
        try {
            LicenseStorage storage = new LicenseStorage(LicensingConfig.getStorageDirectory());
            List<CustomerMessage> diskMsgs = storage.loadInboxMessages();
            MachineIdentity mid = new MachineIdentity(LicensingConfig.getStorageDirectory());
            String machineId = mid.getMachineId();

            for (CustomerMessage cm : diskMsgs) {
                if (cm.getMessageId() == null || cm.getMessageId().isBlank()) continue;
                String eventKey = "management:broadcast:" + machineId + ":" + cm.getMessageId().trim();
                if (!notificationRepository.existsByFirmIdAndEventKey(Notification.GLOBAL_FIRM_ID, eventKey)) {
                    Notification n = Notification.builder()
                            .firmId(Notification.GLOBAL_FIRM_ID)
                            .eventKey(eventKey)
                            .category(NotificationCategory.LICENSING)
                            .priority(NotificationPriority.HIGH)
                            .title(cm.getTitle() != null ? cm.getTitle() : "Announcement")
                            .body(cm.getBody())
                            .sender("RupeeCRM Management")
                            .status(cm.isRead() ? NotificationStatus.READ : NotificationStatus.UNREAD)
                            .createdAt(cm.getCreatedAt() != null ? LocalDateTime.ofInstant(cm.getCreatedAt(), java.time.ZoneId.systemDefault()) : LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    notificationRepository.save(n);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Duration parseDuration(String code) {
        if (code == null || code.isBlank()) return Duration.ofDays(1);
        String clean = code.trim().toLowerCase();
        if ("1h".equals(clean)) return Duration.ofHours(1);
        if ("4h".equals(clean)) return Duration.ofHours(4);
        if ("1d".equals(clean) || "1".equals(clean)) return Duration.ofDays(1);
        if ("3d".equals(clean) || "3".equals(clean)) return Duration.ofDays(3);
        if ("7d".equals(clean) || "7".equals(clean) || "1w".equals(clean)) return Duration.ofDays(7);
        if ("30d".equals(clean) || "30".equals(clean)) return Duration.ofDays(30);
        return Duration.ofDays(1);
    }
}
