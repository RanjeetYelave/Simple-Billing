package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.dto.NotificationRequest;
import com.billing.simple.billsoft.entities.*;
import com.billing.simple.billsoft.repo.InboxMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class InboxMessageService {
    private final InboxMessageRepository repository;

    @Autowired(required = false)
    @Lazy
    private NotificationService notificationService;

    public InboxMessageService(InboxMessageRepository repository) {
        this.repository = repository;
    }

    public List<InboxMessage> getMessagesByFirm(Long firmId) {
        Long authoritativeFirmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (authoritativeFirmId != null && !authoritativeFirmId.equals(firmId)) {
            throw new com.billing.simple.billsoft.security.TenantSecurityException("Cross-firm access prohibited");
        }
        return repository.findByFirmIdOrderByCreatedAtDesc(authoritativeFirmId != null ? authoritativeFirmId : firmId);
    }

    @Transactional
    public InboxMessage createMessage(InboxMessage msg) {
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (firmId != null) {
            msg.setFirmId(firmId);
        }
        InboxMessage saved = repository.save(msg);

        // Sync to canonical notification store
        if (notificationService != null) {
            String eventKey = msg.getReminderId() != null
                    ? "planner:reminder:" + msg.getReminderId()
                    : "legacy:inbox:" + (msg.getFirmId() != null ? msg.getFirmId() : "0") + ":" + saved.getId();

            notificationService.createOrUpdate(NotificationRequest.builder()
                    .firmId(msg.getFirmId())
                    .eventKey(eventKey)
                    .category(msg.getReminderId() != null ? NotificationCategory.PLANNER : NotificationCategory.SYSTEM)
                    .priority(NotificationPriority.NORMAL)
                    .title(msg.getSubject() != null ? msg.getSubject() : "Message")
                    .body(msg.getBody())
                    .sender(msg.getSender() != null ? msg.getSender() : "System")
                    .build());
        }

        return saved;
    }

    @Transactional
    public InboxMessage markAsRead(Long id) {
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        InboxMessage msg = (firmId != null)
                ? repository.findByIdAndFirmId(id, firmId).orElseThrow(() -> new IllegalArgumentException("Message not found"))
                : repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Message not found"));
        msg.setRead(true);
        return msg;
    }

    @Transactional
    public boolean deleteMessage(Long id) {
        Long firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        if (firmId != null) {
            if (repository.existsByIdAndFirmId(id, firmId)) {
                repository.deleteByIdAndFirmId(id, firmId);
                return true;
            }
            if (repository.existsById(id)) {
                repository.deleteById(id);
                return true;
            }
            return false;
        }
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }

    @Transactional
    public boolean sendNotificationIfAbsent(Long firmId, String subjectPrefix, String fullSubject, String body, String sender) {
        if (firmId == null) {
            firmId = com.billing.simple.billsoft.security.TenantContext.getCurrentFirmId();
        }
        if (firmId == null) {
            return false;
        }

        // Forward to canonical notification service with deterministic eventKey
        if (notificationService != null) {
            String eventKey = generateEventKeyForPrefix(firmId, subjectPrefix, fullSubject);
            NotificationCategory cat = determineCategoryForPrefix(subjectPrefix);
            notificationService.createOrUpdate(NotificationRequest.builder()
                    .firmId(firmId)
                    .eventKey(eventKey)
                    .category(cat)
                    .priority(NotificationPriority.HIGH)
                    .title(fullSubject)
                    .body(body)
                    .sender(sender)
                    .build());
        }

        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        boolean exists = repository.existsByFirmIdAndSubjectStartingWithAndCreatedAtAfter(firmId, subjectPrefix, cutoff);
        if (exists) {
            return false;
        }
        InboxMessage msg = InboxMessage.builder()
                .firmId(firmId)
                .subject(fullSubject)
                .body(body)
                .sender(sender)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        repository.save(msg);
        return true;
    }

    private String generateEventKeyForPrefix(Long firmId, String prefix, String subject) {
        String clean = (prefix != null ? prefix : subject).toLowerCase().replaceAll("[^a-z0-9:#_-]", "");
        return "event:" + firmId + ":" + clean;
    }

    private NotificationCategory determineCategoryForPrefix(String prefix) {
        if (prefix == null) return NotificationCategory.SYSTEM;
        if (prefix.contains("Invoice")) return NotificationCategory.BILLING;
        if (prefix.contains("Inventory")) return NotificationCategory.INVENTORY;
        if (prefix.contains("Delivery") || prefix.contains("PO")) return NotificationCategory.PURCHASE;
        if (prefix.contains("Payroll")) return NotificationCategory.HR;
        if (prefix.contains("Reminder") || prefix.contains("Task")) return NotificationCategory.PLANNER;
        return NotificationCategory.SYSTEM;
    }

    /**
     * Aggregates all low stock and out-of-stock items for a firm into a single consolidated notification.
     */
    @Transactional
    public void notifyAggregatedLowStock(Long firmId, List<Product> products) {
        if (firmId == null || products == null || products.isEmpty()) {
            return;
        }

        List<Product> outOfStock = new ArrayList<>();
        List<Product> lowStock = new ArrayList<>();

        for (Product p : products) {
            if ("SERVICE".equalsIgnoreCase(p.getItemType())) {
                continue;
            }
            BigDecimal stock = p.getStockQuantity() != null ? p.getStockQuantity() : BigDecimal.ZERO;
            BigDecimal min = p.getMinStockLevel() != null ? p.getMinStockLevel() : new BigDecimal("5.000");

            if (stock.compareTo(BigDecimal.ZERO) <= 0) {
                outOfStock.add(p);
            } else if (stock.compareTo(min) <= 0) {
                lowStock.add(p);
            }
        }

        int totalAlertItems = outOfStock.size() + lowStock.size();
        if (totalAlertItems == 0) {
            return;
        }

        String subject;
        if (!outOfStock.isEmpty() && !lowStock.isEmpty()) {
            subject = String.format("⚠️ Inventory Alert: %d Items Require Attention (%d Out of Stock, %d Low)",
                    totalAlertItems, outOfStock.size(), lowStock.size());
        } else if (!outOfStock.isEmpty()) {
            subject = String.format("🔴 Inventory Alert: %d %s Out of Stock",
                    outOfStock.size(), outOfStock.size() == 1 ? "Item" : "Items");
        } else {
            subject = String.format("⚠️ Inventory Alert: %d %s Low on Stock",
                    lowStock.size(), lowStock.size() == 1 ? "Item" : "Items");
        }

        StringBuilder body = new StringBuilder();
        body.append("Consolidated Inventory Alert: Several items have reached or breached their safety reorder thresholds.\n\n");

        if (!outOfStock.isEmpty()) {
            body.append("🔴 OUT OF STOCK (").append(outOfStock.size()).append("):\n");
            int limit = Math.min(outOfStock.size(), 10);
            for (int i = 0; i < limit; i++) {
                Product p = outOfStock.get(i);
                String skuStr = (p.getSku() != null && !p.getSku().isBlank()) ? " [SKU: " + p.getSku() + "]" : "";
                String unitStr = p.getUnit() != null ? p.getUnit() : "pcs";
                body.append("• ").append(p.getName()).append(skuStr)
                        .append(" — 0 ").append(unitStr).append(" (Min Threshold: ")
                        .append(p.getMinStockLevel() != null ? p.getMinStockLevel().stripTrailingZeros().toPlainString() : "0")
                        .append(" ").append(unitStr).append(")\n");
            }
            if (outOfStock.size() > 10) {
                body.append("... and ").append(outOfStock.size() - 10).append(" more out-of-stock items.\n");
            }
            body.append("\n");
        }

        if (!lowStock.isEmpty()) {
            body.append("🟡 LOW STOCK (").append(lowStock.size()).append("):\n");
            int limit = Math.min(lowStock.size(), 10);
            for (int i = 0; i < limit; i++) {
                Product p = lowStock.get(i);
                String skuStr = (p.getSku() != null && !p.getSku().isBlank()) ? " [SKU: " + p.getSku() + "]" : "";
                String unitStr = p.getUnit() != null ? p.getUnit() : "pcs";
                String currentStock = p.getStockQuantity() != null ? p.getStockQuantity().stripTrailingZeros().toPlainString() : "0";
                String minStock = p.getMinStockLevel() != null ? p.getMinStockLevel().stripTrailingZeros().toPlainString() : "0";
                body.append("• ").append(p.getName()).append(skuStr)
                        .append(" — ").append(currentStock).append(" ").append(unitStr)
                        .append(" remaining (Min Threshold: ").append(minStock).append(" ").append(unitStr).append(")\n");
            }
            if (lowStock.size() > 10) {
                body.append("... and ").append(lowStock.size() - 10).append(" more low-stock items.\n");
            }
            body.append("\n");
        }

        body.append("Action Required: Please visit the Inventory Manager to adjust counts or generate Purchase Orders to replenish supply.");

        // Canonical Notification with deterministic eventKey
        if (notificationService != null) {
            notificationService.createOrUpdate(NotificationRequest.builder()
                    .firmId(firmId)
                    .eventKey("inventory:low-stock:aggregate:" + firmId)
                    .category(NotificationCategory.INVENTORY)
                    .priority(NotificationPriority.HIGH)
                    .title(subject)
                    .body(body.toString())
                    .sender("Inventory System")
                    .primaryActionLabel("Open Inventory Catalog")
                    .primaryActionType(NotificationActionType.NAVIGATE)
                    .primaryActionTarget("{\"page\":\"firm\",\"tab\":\"inventory\"}")
                    .secondaryActionLabel("Create Purchase Order")
                    .secondaryActionType(NotificationActionType.NAVIGATE)
                    .secondaryActionTarget("{\"page\":\"paperwork\",\"tab\":\"orders\"}")
                    .build());
        }

        // Backward compatibility: keep legacy check
        List<InboxMessage> unread = repository.findByFirmIdAndIsReadFalse(firmId);
        boolean alreadyHasUnread = unread.stream().anyMatch(m ->
                m.getSubject() != null && (m.getSubject().startsWith("⚠️ Inventory Alert:") || m.getSubject().startsWith("🔴 Inventory Alert:")));

        if (!alreadyHasUnread) {
            InboxMessage msg = InboxMessage.builder()
                    .firmId(firmId)
                    .subject(subject)
                    .body(body.toString())
                    .sender("Inventory System")
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            repository.save(msg);
        }
    }
}
