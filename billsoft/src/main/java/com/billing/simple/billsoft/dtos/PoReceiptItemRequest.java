package com.billing.simple.billsoft.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoReceiptItemRequest {
    private Long purchaseOrderItemId;
    private Long productId;
    private BigDecimal receivedQuantity;
    private String unit;
    private String notes;
}
