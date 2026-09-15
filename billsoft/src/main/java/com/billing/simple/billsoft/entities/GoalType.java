package com.billing.simple.billsoft.entities;

/**
 * Defines polymorphic goal behavior across financial, habit, and milestone tracking.
 */
public enum GoalType {
    SAVINGS_TARGET,   // Target amount with % progress & automatic sync from linked Savings entries
    HABIT_STREAK,     // Positive daily streak (e.g. "Daily 30-min Reading", "Daily Customer Calls")
    QUIT_HABIT,       // Avoidance streak (e.g. "No Smoking", "Zero Impulsive Purchases") with relapse resets
    LIFE_MILESTONE    // Numerical milestone counter (e.g. "Onboard 50 Dealers", "Run 100km")
}
