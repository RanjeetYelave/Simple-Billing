package com.billing.simple.billsoft.dtos;

import lombok.Data;

@Data
public class InvoiceRequestItem {
	private Long productId;
	private Integer qty;
}
