package com.billing.simple.billsoft.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoReceiptRequest {
    private LocalDate receiptDate;
    private String receivedBy;
    private String notes;
    private String idempotencyKey;

    @Builder.Default
    private List<PoReceiptItemRequest> items = new ArrayList<>();
}
