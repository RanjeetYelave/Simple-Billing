package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NotificationActionType {
    NAVIGATE,
    MODAL,
    API_ACTION,
    UNKNOWN;

    @JsonCreator
    public static NotificationActionType from(Object value) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
            return null;
        }
        try {
            int ordinal = Integer.parseInt(str);
            NotificationActionType[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return UNKNOWN;
        } catch (NumberFormatException ignored) {
        }
        for (NotificationActionType a : values()) {
            if (a.name().equalsIgnoreCase(str)) {
                return a;
            }
        }
        return UNKNOWN;
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
