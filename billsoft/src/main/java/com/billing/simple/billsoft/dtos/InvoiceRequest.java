package com.billing.simple.billsoft.dtos;


import java.util.List;

import lombok.Data;

@Data
public class InvoiceRequest {
    private Long customerId;
    private List<InvoiceRequestItem> items;
    private String notes;
}
