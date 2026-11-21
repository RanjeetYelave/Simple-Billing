package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NotificationCategory {
    LICENSING,  // 0
    BILLING,    // 1
    INVENTORY,  // 2
    PURCHASE,   // 3
    PLANNER,    // 4
    HR,         // 5
    SYSTEM,     // 6
    UNKNOWN;    // 7

    @JsonCreator
    public static NotificationCategory from(Object value) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
            return null;
        }
        try {
            int ordinal = Integer.parseInt(str);
            NotificationCategory[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return UNKNOWN;
        } catch (NumberFormatException ignored) {
        }
        for (NotificationCategory cat : values()) {
            if (cat.name().equalsIgnoreCase(str)) {
                return cat;
            }
        }
        return UNKNOWN;
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
