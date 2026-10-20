package com.billing.simple.billsoft.events;

import com.billing.simple.billsoft.entities.PurchaseOrderStatus;

import java.util.Collections;
import java.util.Set;

/**
 * Event published when a Purchase Order is created, updated, changes status, or is deleted.
 * Used for targeted recalculation of restock advisory recommendations without catalog scans.
 */
public class PurchaseOrderUpdatedEvent {

    private final Long poId;
    private final Long firmId;
    private final PurchaseOrderStatus status;
    private final PurchaseOrderStatus oldStatus;
    private final Set<Long> productIds;

    public PurchaseOrderUpdatedEvent(Long poId, Long firmId, PurchaseOrderStatus status, PurchaseOrderStatus oldStatus, Set<Long> productIds) {
        this.poId = poId;
        this.firmId = firmId;
        this.status = status;
        this.oldStatus = oldStatus;
        this.productIds = (productIds != null) ? Collections.unmodifiableSet(productIds) : Collections.emptySet();
    }

    public Long getPoId() {
        return poId;
    }

    public Long getFirmId() {
        return firmId;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public PurchaseOrderStatus getOldStatus() {
        return oldStatus;
    }

    public Set<Long> getProductIds() {
        return productIds;
    }

    @Override
    public String toString() {
        return "PurchaseOrderUpdatedEvent{" +
                "poId=" + poId +
                ", firmId=" + firmId +
                ", status=" + status +
                ", oldStatus=" + oldStatus +
                ", productIds=" + productIds +
                '}';
    }
}
