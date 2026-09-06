package com.billing.simple.billsoft.entities;

public enum InvoiceStatus {
    DRAFT,          // Saved as draft (no stock deduction, not in sales reports)
    UNPAID,         // Finalized unpaid invoice (stock deducted, in sales reports)
    PAID,           // Finalized paid invoice (stock deducted, in sales reports)
    CANCELLED,      // Voided invoice (stock restored to inventory, removed from sales)
    ESTIMATE,       // Quotation / Estimate
    FINAL,          // Legacy alias for UNPAID/FINAL
    SENT,           // Legacy alias for UNPAID
    OVERDUE         // Legacy alias for UNPAID past due
}
