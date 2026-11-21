package com.billing.simple.billsoft.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Defines polymorphic goal behavior across financial, habit, and milestone tracking.
 */
public enum GoalType {
    SAVINGS_TARGET,   // Target amount with % progress & automatic sync from linked Savings entries
    HABIT_STREAK,     // Positive daily streak (e.g. "Daily 30-min Reading", "Daily Customer Calls")
    QUIT_HABIT,       // Avoidance streak (e.g. "No Smoking", "Zero Impulsive Purchases") with relapse resets
    LIFE_MILESTONE;   // Numerical milestone counter (e.g. "Onboard 50 Dealers", "Run 100km")

    @JsonCreator
    public static GoalType from(Object value) {
        if (value == null) {
            return SAVINGS_TARGET;
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
            return SAVINGS_TARGET;
        }
        try {
            int ordinal = Integer.parseInt(str);
            GoalType[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return SAVINGS_TARGET;
        } catch (NumberFormatException ignored) {
        }
        for (GoalType g : values()) {
            if (g.name().equalsIgnoreCase(str)) {
                return g;
            }
        }
        return SAVINGS_TARGET;
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
