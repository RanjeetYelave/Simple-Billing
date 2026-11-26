package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CollectionActivityType {
    PAYMENT_RECEIVED,
    REMINDER_PREPARED,
    WHATSAPP_OPENED,
    FOLLOW_UP_RECORDED,
    FOLLOW_UP_COMPLETED,
    PROMISE_CREATED,
    PROMISE_MISSED,
    PROMISE_FULFILLED,
    PROMISE_RESCHEDULED,
    NOTE_ADDED,
    CALL_LOGGED;

    @JsonCreator
    public static CollectionActivityType from(Object value) {
        if (value == null) {
            return NOTE_ADDED;
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
            return NOTE_ADDED;
        }
        for (CollectionActivityType t : values()) {
            if (t.name().equalsIgnoreCase(str)) {
                return t;
            }
        }
        return NOTE_ADDED;
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
