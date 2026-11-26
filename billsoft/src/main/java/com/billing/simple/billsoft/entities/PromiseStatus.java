package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PromiseStatus {
    PROMISED,
    PARTIALLY_FULFILLED,
    FULFILLED,
    MISSED,
    RESCHEDULED,
    CANCELLED;

    @JsonCreator
    public static PromiseStatus from(Object value) {
        if (value == null) {
            return PROMISED;
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
            return PROMISED;
        }
        try {
            int ordinal = Integer.parseInt(str);
            PromiseStatus[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return PROMISED;
        } catch (NumberFormatException ignored) {
        }
        for (PromiseStatus s : values()) {
            if (s.name().equalsIgnoreCase(str)) {
                return s;
            }
        }
        return PROMISED;
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
