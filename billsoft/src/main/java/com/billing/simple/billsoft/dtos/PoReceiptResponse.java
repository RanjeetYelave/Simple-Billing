package com.billing.simple.billsoft.dtos;

import com.billing.simple.billsoft.entities.PurchaseOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoReceiptResponse {
    private Long receiptId;
    private Long purchaseOrderId;
    private String poNumber;
    private String receiptNumber;
    private LocalDate receiptDate;
    private String receivedBy;
    private String notes;
    private PurchaseOrderStatus poStatus;
    private LocalDateTime createdAt;
    private List<PoReceiptItemDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PoReceiptItemDto {
        private Long receiptItemId;
        private Long purchaseOrderItemId;
        private Long productId;
        private String productName;
        private BigDecimal receivedQuantity;
        private String unit;
    }
}
