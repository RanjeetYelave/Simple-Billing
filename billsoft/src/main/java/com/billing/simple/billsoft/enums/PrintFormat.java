package com.billing.simple.billsoft.enums;

import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;

public enum PrintFormat {
    A3("A3", PageSize.A3, 36f, 36f, 36f, 36f),
    A4("A4", PageSize.A4, 28f, 28f, 28f, 38f),
    A5("A5", PageSize.A5, 20f, 20f, 20f, 30f),
    THERMAL_80MM("Thermal 80mm", new Rectangle(226.77f, 842.0f), 6f, 6f, 6f, 6f);

    private final String displayName;
    private final Rectangle pageSize;
    private final float marginLeft;
    private final float marginRight;
    private final float marginTop;
    private final float marginBottom;

    PrintFormat(String displayName, Rectangle pageSize, float marginLeft, float marginRight, float marginTop, float marginBottom) {
        this.displayName = displayName;
        this.pageSize = pageSize;
        this.marginLeft = marginLeft;
        this.marginRight = marginRight;
        this.marginTop = marginTop;
        this.marginBottom = marginBottom;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Rectangle getPageSize() {
        return pageSize;
    }

    public float getMarginLeft() { return marginLeft; }
    public float getMarginRight() { return marginRight; }
    public float getMarginTop() { return marginTop; }
    public float getMarginBottom() { return marginBottom; }

    public static PrintFormat fromString(String val) {
        if (val == null || val.isBlank() || val.contains("object")) return A4;
        String s = val.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        if (s.equals("A3")) return A3;
        if (s.equals("A5")) return A5;
        if (s.contains("THERMAL") || s.contains("80") || s.equals("POS")) return THERMAL_80MM;
        return A4;
    }
}
