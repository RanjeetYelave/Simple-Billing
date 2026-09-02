package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.InboxMessage;
import com.billing.simple.billsoft.entities.Product;
import com.billing.simple.billsoft.repo.InboxMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class InboxMessageService {
    private final InboxMessageRepository repository;

    public InboxMessageService(InboxMessageRepository repository) {
        this.repository = repository;
    }

    public List<InboxMessage> getMessagesByFirm(Long firmId) {
        return repository.findByFirmIdOrderByCreatedAtDesc(firmId);
    }

    public InboxMessage createMessage(InboxMessage msg) {
        return repository.save(msg);
    }

    @Transactional
    public InboxMessage markAsRead(Long id) {
        InboxMessage msg = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Message not found"));
        msg.setRead(true);
        return msg;
    }

    @Transactional
    public boolean deleteMessage(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }

    @Transactional
    public boolean sendNotificationIfAbsent(Long firmId, String subjectPrefix, String fullSubject, String body, String sender) {
        if (firmId == null) {
            firmId = 1L;
        }
        List<InboxMessage> unread = repository.findByFirmIdAndIsReadFalse(firmId);
        boolean exists = unread.stream().anyMatch(m -> m.getSubject() != null && m.getSubject().startsWith(subjectPrefix));
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
        String subjectPrefix = "⚠️ Inventory Alert:";
        if (!outOfStock.isEmpty() && !lowStock.isEmpty()) {
            subject = String.format("⚠️ Inventory Alert: %d Items Require Attention (%d Out of Stock, %d Low)",
                    totalAlertItems, outOfStock.size(), lowStock.size());
        } else if (!outOfStock.isEmpty()) {
            subjectPrefix = "🔴 Inventory Alert:";
            subject = String.format("🔴 Inventory Alert: %d %s Out of Stock",
                    outOfStock.size(), outOfStock.size() == 1 ? "Item" : "Items");
        } else {
            subject = String.format("⚠️ Inventory Alert: %d %s Low on Stock",
                    lowStock.size(), lowStock.size() == 1 ? "Item" : "Items");
        }

        // Check if an unread alert already exists with same prefix
        List<InboxMessage> unread = repository.findByFirmIdAndIsReadFalse(firmId);
        boolean alreadyHasUnread = unread.stream().anyMatch(m ->
                m.getSubject() != null && (m.getSubject().startsWith("⚠️ Inventory Alert:") || m.getSubject().startsWith("🔴 Inventory Alert:")));

        if (alreadyHasUnread) {
            return;
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
