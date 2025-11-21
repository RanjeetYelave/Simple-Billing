package com.billing.simple.billsoft.dtos;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvoiceUpdateRequest {

    private Long customerId;
    private String invoiceDate;
    private String notes;

    private List<ItemData> items;

    @Getter
    @Setter
    public static class ItemData {
        private Long itemId;        // null for NEW items
        private Long productId;     // can be changed
        private Double price;       // editable
        private Double gstPercentage;
        private Integer quantity;
        private Boolean remove;     // optional flag for deletion
    }
}
