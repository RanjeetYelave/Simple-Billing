package com.billing.simple.billsoft.dtos;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvoiceRequest {

    private Long customerId;
    private String notes;

    private List<InvoiceRequestItem> items;
}
