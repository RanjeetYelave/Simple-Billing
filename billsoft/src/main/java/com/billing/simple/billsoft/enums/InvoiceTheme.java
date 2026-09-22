package com.billing.simple.billsoft.enums;

public enum InvoiceTheme {
    CLASSIC("Classic"),
    MODERN("Modern"),
    PROFESSIONAL("Professional"),
    MINIMAL("Minimal");

    private final String displayName;

    InvoiceTheme(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static InvoiceTheme fromString(String val) {
        if (val == null || val.isBlank()) return CLASSIC;
        for (InvoiceTheme t : values()) {
            if (t.name().equalsIgnoreCase(val.trim()) || t.displayName.equalsIgnoreCase(val.trim())) {
                return t;
            }
        }
        return CLASSIC;
    }
}
