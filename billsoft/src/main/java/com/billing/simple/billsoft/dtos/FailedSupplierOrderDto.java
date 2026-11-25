package com.billing.simple.billsoft.dtos;

import com.billing.simple.billsoft.entities.PurchaseOrderItem;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailedSupplierOrderDto {
    private Long partyId;
    private String partyName;
    private String errorCode;
    private String errorMessage;
    @Builder.Default
    private List<PurchaseOrderItem> items = new ArrayList<>();
}
