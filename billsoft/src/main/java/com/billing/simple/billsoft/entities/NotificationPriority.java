package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NotificationPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL,
    UNKNOWN;

    @JsonCreator
    public static NotificationPriority from(Object value) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
            return null;
        }
        try {
            int ordinal = Integer.parseInt(str);
            NotificationPriority[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return UNKNOWN;
        } catch (NumberFormatException ignored) {
        }
        for (NotificationPriority p : values()) {
            if (p.name().equalsIgnoreCase(str)) {
                return p;
            }
        }
        return UNKNOWN;
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
