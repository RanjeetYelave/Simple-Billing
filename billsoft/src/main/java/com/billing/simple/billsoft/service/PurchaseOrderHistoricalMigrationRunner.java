package com.billing.simple.billsoft.service;

import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.billing.simple.billsoft.entities.PurchaseOrderItem;
import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import com.billing.simple.billsoft.repositories.PurchaseOrderItemRepository;
import com.billing.simple.billsoft.repositories.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Historical PO Migration:
 * Backfills receivedQuantity for existing historical PO items where receivedQuantity is null.
 * For RECEIVED POs: sets receivedQuantity = quantity.
 * For DRAFT, ISSUED, CANCELLED POs: sets receivedQuantity = BigDecimal.ZERO.
 * Does NOT generate duplicate stock movements.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseOrderHistoricalMigrationRunner implements ApplicationRunner {

    private final PurchaseOrderRepository poRepository;
    private final PurchaseOrderItemRepository poItemRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            List<PurchaseOrder> pos = poRepository.findAll();
            int migratedItems = 0;
            for (PurchaseOrder po : pos) {
                if (po.getItems() == null || po.getItems().isEmpty()) continue;
                for (PurchaseOrderItem item : po.getItems()) {
                    if (item.getReceivedQuantity() == null) {
                        if (po.getStatus() == PurchaseOrderStatus.RECEIVED) {
                            item.setReceivedQuantity(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO);
                        } else {
                            item.setReceivedQuantity(BigDecimal.ZERO);
                        }
                        poItemRepository.save(item);
                        migratedItems++;
                    }
                }
            }
            if (migratedItems > 0) {
                log.info("Historical PO migration: successfully backfilled receivedQuantity for {} line items.", migratedItems);
            }
        } catch (Exception e) {
            log.warn("Historical PO migration check skipped: {}", e.getMessage());
        }
    }
}
