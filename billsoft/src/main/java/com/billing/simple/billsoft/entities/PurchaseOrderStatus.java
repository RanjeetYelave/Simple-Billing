package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Status lifecycle of a Purchase Order.
 */
public enum PurchaseOrderStatus {
    DRAFT,
    ISSUED,
    RECEIVED,
    CANCELLED;

    @JsonCreator
    public static PurchaseOrderStatus from(Object value) {
        if (value == null) {
            return DRAFT;
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
            return DRAFT;
        }
        try {
            int ordinal = Integer.parseInt(str);
            PurchaseOrderStatus[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return DRAFT;
        } catch (NumberFormatException ignored) {
        }
        for (PurchaseOrderStatus s : values()) {
            if (s.name().equalsIgnoreCase(str)) {
                return s;
            }
        }
        return DRAFT;
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
