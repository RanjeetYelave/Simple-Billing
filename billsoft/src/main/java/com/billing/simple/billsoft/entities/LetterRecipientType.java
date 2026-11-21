package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Type of recipient for an official business letter.
 */
public enum LetterRecipientType {
    PARTY,      // Vendor / Supplier
    CUSTOMER,   // Client / Buyer
    CUSTOM;     // External individual or organization

    @JsonCreator
    public static LetterRecipientType from(Object value) {
        if (value == null) {
            return CUSTOM;
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
            return CUSTOM;
        }
        try {
            int ordinal = Integer.parseInt(str);
            LetterRecipientType[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return CUSTOM;
        } catch (NumberFormatException ignored) {
        }
        for (LetterRecipientType t : values()) {
            if (t.name().equalsIgnoreCase(str)) {
                return t;
            }
        }
        return CUSTOM;
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
