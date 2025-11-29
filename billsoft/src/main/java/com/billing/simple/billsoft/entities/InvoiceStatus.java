package com.billing.simple.billsoft.entities;

public enum InvoiceStatus {
    DRAFT,          // Not finalized, editable
    ESTIMATE,       // Quotation / Estimate
    FINAL,          // Final invoice
    SENT,           // Sent to customer
    PAID,           // Fully paid
    OVERDUE,        // Due date passed & not paid
    CANCELLED       // Voided invoice
}
