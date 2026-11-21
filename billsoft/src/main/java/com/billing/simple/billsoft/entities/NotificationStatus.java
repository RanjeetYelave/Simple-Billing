package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NotificationStatus {
    UNREAD,
    READ,
    SNOOZED,
    DISMISSED,
    ACTIONED,
    EXPIRED,
    UNKNOWN;

    @JsonCreator
    public static NotificationStatus from(Object value) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
            return null;
        }
        try {
            int ordinal = Integer.parseInt(str);
            NotificationStatus[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return UNKNOWN;
        } catch (NumberFormatException ignored) {
        }
        for (NotificationStatus s : values()) {
            if (s.name().equalsIgnoreCase(str)) {
                return s;
            }
        }
        return UNKNOWN;
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
