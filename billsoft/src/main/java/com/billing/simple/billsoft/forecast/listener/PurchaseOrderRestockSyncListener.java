package com.billing.simple.billsoft.forecast.listener;

import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import com.billing.simple.billsoft.events.PurchaseOrderUpdatedEvent;
import com.billing.simple.billsoft.forecast.service.RestockAdvisoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for PurchaseOrderUpdatedEvent after transaction commit and triggers
 * targeted replenishment recommendation recalculation for affected products.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseOrderRestockSyncListener {

    private final RestockAdvisoryService restockAdvisoryService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPurchaseOrderUpdated(PurchaseOrderUpdatedEvent event) {
        if (event == null || event.getFirmId() == null || event.getProductIds() == null || event.getProductIds().isEmpty()) {
            return;
        }

        PurchaseOrderStatus st = event.getStatus();
        PurchaseOrderStatus oldSt = event.getOldStatus();

        // Check if status change affects inventory position (ISSUED, RECEIVED, CANCELLED)
        boolean affectsStock = (st == PurchaseOrderStatus.ISSUED || oldSt == PurchaseOrderStatus.ISSUED
                || st == PurchaseOrderStatus.RECEIVED || oldSt == PurchaseOrderStatus.RECEIVED
                || st == PurchaseOrderStatus.CANCELLED || oldSt == PurchaseOrderStatus.CANCELLED);

        if (!affectsStock) {
            return;
        }

        try {
            log.info("Refreshing restock recommendations for {} products after PO {} update (status: {}, oldStatus: {}) in firm {}",
                    event.getProductIds().size(), event.getPoId(), st, oldSt, event.getFirmId());
            restockAdvisoryService.refreshRecommendationsForProducts(event.getFirmId(), event.getProductIds());
        } catch (Exception ex) {
            log.error("Failed to refresh restock recommendations for PO {} event", event.getPoId(), ex);
        }
    }
}
