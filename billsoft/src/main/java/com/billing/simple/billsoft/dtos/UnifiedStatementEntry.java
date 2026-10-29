package com.billing.simple.billsoft.dtos;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnifiedStatementEntry {
    private LocalDate date;
    private String side; // "CUSTOMER_RECEIVABLE" or "SUPPLIER_PAYABLE"
    private String type; // "INVOICE", "SALES_RETURN", "CUSTOMER_PAYMENT", "PURCHASE_ORDER", "VENDOR_PAYMENT"
    private String ref;
    private String description;
    private Double receivableDebit;  // sales invoice billed (customer owes)
    private Double receivableCredit; // customer payment or return
    private Double payableDebit;     // vendor purchase order liability (we owe)
    private Double payableCredit;    // payment made to vendor
}
