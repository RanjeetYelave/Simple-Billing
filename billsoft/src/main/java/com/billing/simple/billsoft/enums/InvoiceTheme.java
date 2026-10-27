package com.billing.simple.billsoft.enums;

public enum InvoiceTheme {
    CLASSIC("Classic"),
    MODERN("Modern"),
    PROFESSIONAL("Professional"),
    @Deprecated
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
        String s = val.trim();
        if (s.equalsIgnoreCase("MINIMAL")) {
            return MODERN; // Legacy identifier gracefully resolves to authoritative Modern theme
        }
        for (InvoiceTheme t : values()) {
            if (t.name().equalsIgnoreCase(s) || t.displayName.equalsIgnoreCase(s)) {
                return t;
            }
        }
        return CLASSIC;
    }
}
