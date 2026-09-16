package com.billing.simple.billsoft.dtos;

import com.billing.simple.billsoft.entities.PurchaseOrder;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchPurchaseOrderRequest {

    private String clientRequestId;
    private Long firmId;
    private Boolean hidePricesOnPo;
    private LocalDate expectedDeliveryDate;
    private String notes;

    @Builder.Default
    private List<PurchaseOrder> orders = new ArrayList<>();

    @Builder.Default
    @JsonAlias({"preferredVendorUpdates", "updatePreferredVendors"})
    private List<PreferredVendorUpdate> updatePreferredVendors = new ArrayList<>();
}

