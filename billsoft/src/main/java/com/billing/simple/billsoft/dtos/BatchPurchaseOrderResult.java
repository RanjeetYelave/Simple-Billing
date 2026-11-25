package com.billing.simple.billsoft.dtos;

import com.billing.simple.billsoft.entities.PurchaseOrder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of batch purchase order creation.
 * Extends ArrayList&lt;PurchaseOrder&gt; so legacy callers expecting List&lt;PurchaseOrder&gt;
 * remain 100% backward compatible without code breaks, while carrying per-supplier failure details.
 */
@Getter
@Setter
public class BatchPurchaseOrderResult extends ArrayList<PurchaseOrder> {

    private String clientRequestId;
    private Long firmId;
    private int totalOrdersRequested;
    private final List<FailedSupplierOrderDto> failedOrders = new ArrayList<>();

    public BatchPurchaseOrderResult() {
        super();
    }

    public BatchPurchaseOrderResult(int initialCapacity) {
        super(initialCapacity);
    }

    public boolean hasFailures() {
        return !failedOrders.isEmpty();
    }

    public int getSuccessfulCount() {
        return this.size();
    }

    public int getFailedCount() {
        return failedOrders.size();
    }

    public BatchPurchaseOrderResponse toResponseDto() {
        return BatchPurchaseOrderResponse.builder()
                .clientRequestId(this.clientRequestId)
                .firmId(this.firmId)
                .totalOrders(this.totalOrdersRequested)
                .successfulCount(this.size())
                .failedCount(this.failedOrders.size())
                .createdOrders(new ArrayList<>(this))
                .failedOrders(new ArrayList<>(this.failedOrders))
                .build();
    }
}
