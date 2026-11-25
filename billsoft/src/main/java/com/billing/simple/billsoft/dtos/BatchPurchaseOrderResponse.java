package com.billing.simple.billsoft.dtos;

import com.billing.simple.billsoft.entities.PurchaseOrder;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchPurchaseOrderResponse {
    private String clientRequestId;
    private Long firmId;
    private int totalOrders;
    private int successfulCount;
    private int failedCount;

    @Builder.Default
    private List<PurchaseOrder> createdOrders = new ArrayList<>();

    @Builder.Default
    private List<FailedSupplierOrderDto> failedOrders = new ArrayList<>();
}
