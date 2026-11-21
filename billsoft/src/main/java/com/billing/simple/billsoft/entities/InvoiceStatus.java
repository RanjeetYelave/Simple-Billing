package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum InvoiceStatus {
    DRAFT,          // Saved as draft (no stock deduction, not in sales reports)
    UNPAID,         // Finalized unpaid invoice (stock deducted, in sales reports)
    PAID,           // Finalized paid invoice (stock deducted, in sales reports)
    CANCELLED,      // Voided invoice (stock restored to inventory, removed from sales)
    ESTIMATE,       // Quotation / Estimate
    FINAL,          // Legacy alias for UNPAID/FINAL
    SENT,           // Legacy alias for UNPAID
    OVERDUE;        // Legacy alias for UNPAID past due

    @JsonCreator
    public static InvoiceStatus from(Object value) {
        if (value == null) {
            return UNPAID;
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
            return UNPAID;
        }
        try {
            int ordinal = Integer.parseInt(str);
            InvoiceStatus[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return UNPAID;
        } catch (NumberFormatException ignored) {
        }
        for (InvoiceStatus s : values()) {
            if (s.name().equalsIgnoreCase(str)) {
                return s;
            }
        }
        return UNPAID;
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
