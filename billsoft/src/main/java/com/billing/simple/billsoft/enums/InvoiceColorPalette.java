package com.billing.simple.billsoft.enums;

import java.awt.Color;

public enum InvoiceColorPalette {
    CLASSIC_BLUE("Classic Blue", "#007AA6", "#E6F2F7", "#004863"),
    MODERN_TEAL("Modern Teal",   "#0D9488", "#F0FDFA", "#115E59"),
    INDIGO_NAVY("Indigo Navy",   "#4F46E5", "#EEF2FF", "#3730A3"),
    EMERALD_GREEN("Emerald",     "#059669", "#ECFDF5", "#065F46"),
    SLATE_DARK("Slate Dark",     "#334155", "#F8FAFC", "#0F172A");

    private final String displayName;
    private final String primaryHex;
    private final String lightTintHex;
    private final String darkShadeHex;
    private final Color primaryColor;
    private final Color lightTintColor;
    private final Color darkShadeColor;

    InvoiceColorPalette(String displayName, String primaryHex, String lightTintHex, String darkShadeHex) {
        this.displayName = displayName;
        this.primaryHex = primaryHex;
        this.lightTintHex = lightTintHex;
        this.darkShadeHex = darkShadeHex;
        this.primaryColor = parseHex(primaryHex);
        this.lightTintColor = parseHex(lightTintHex);
        this.darkShadeColor = parseHex(darkShadeHex);
    }

    public String getDisplayName() { return displayName; }
    public String getPrimaryHex() { return primaryHex; }
    public String getLightTintHex() { return lightTintHex; }
    public String getDarkShadeHex() { return darkShadeHex; }
    public Color getPrimaryColor() { return primaryColor; }
    public Color getLightTintColor() { return lightTintColor; }
    public Color getDarkShadeColor() { return darkShadeColor; }

    public static InvoiceColorPalette resolve(String input) {
        if (input == null || input.isBlank()) return CLASSIC_BLUE;
        String s = input.trim();
        for (InvoiceColorPalette p : values()) {
            if (p.name().equalsIgnoreCase(s)
                    || p.displayName.equalsIgnoreCase(s)
                    || p.primaryHex.equalsIgnoreCase(s)) {
                return p;
            }
        }
        if (s.startsWith("#")) {
            for (InvoiceColorPalette p : values()) {
                if (p.primaryHex.equalsIgnoreCase(s)) return p;
            }
        }
        return CLASSIC_BLUE;
    }

    private static Color parseHex(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new Color(r, g, b);
        } catch (Exception e) {
            return new Color(0, 122, 166);
        }
    }
}
