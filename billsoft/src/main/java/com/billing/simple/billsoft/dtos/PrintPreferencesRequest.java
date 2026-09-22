package com.billing.simple.billsoft.dtos;

public class PrintPreferencesRequest {
    private String theme;
    private String color;
    private String format;

    public PrintPreferencesRequest() {}

    public PrintPreferencesRequest(String theme, String color, String format) {
        this.theme = theme;
        this.color = color;
        this.format = format;
    }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
}
