package com.billing.simple.billsoft.dtos.debtor;

import com.billing.simple.billsoft.entities.CollectionActivityType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogActivityRequest {
    private Long customerId;
    private Long invoiceId;
    private Long paymentId;
    private Long promiseId;
    private CollectionActivityType activityType;
    private String description;
}
