package com.billing.simple.billsoft.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesReturnRequest {

    private Long firmId;
    private Long invoiceId;
    private String returnNumber;
    private LocalDate returnDate;
    private String reason;
    private String refundMode; // "CASH", "BANK_TRANSFER", "UPI", "CUSTOMER_CREDIT"
    private String notes;
    private List<SalesReturnItemRequest> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SalesReturnItemRequest {
        private Long invoiceItemId;
        private Long productId;
        private String productName;
        private Integer returnQty;
        private BigDecimal unitPrice;
        private BigDecimal discountValue;
        private BigDecimal gstPercent;
    }
}
