package com.billing.simple.billsoft.dtos;

import com.billing.simple.billsoft.entities.FirmDetails;
import com.billing.simple.billsoft.enums.InvoiceColorPalette;
import com.billing.simple.billsoft.enums.InvoiceTheme;
import com.billing.simple.billsoft.enums.PrintFormat;

public class InvoicePrintOptions {

    private final InvoiceTheme theme;
    private final PrintFormat format;
    private final InvoiceColorPalette colorPalette;

    public InvoicePrintOptions(InvoiceTheme theme, PrintFormat format, InvoiceColorPalette colorPalette) {
        this.theme = theme != null ? theme : InvoiceTheme.CLASSIC;
        this.format = format != null ? format : PrintFormat.A4;
        this.colorPalette = colorPalette != null ? colorPalette : InvoiceColorPalette.CLASSIC_BLUE;
    }

    public InvoiceTheme getTheme() { return theme; }
    public PrintFormat getFormat() { return format; }
    public InvoiceColorPalette getColorPalette() { return colorPalette; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private InvoiceTheme theme = InvoiceTheme.CLASSIC;
        private PrintFormat format = PrintFormat.A4;
        private InvoiceColorPalette colorPalette = InvoiceColorPalette.CLASSIC_BLUE;

        public Builder theme(String themeStr) {
            this.theme = InvoiceTheme.fromString(themeStr);
            return this;
        }

        public Builder theme(InvoiceTheme theme) {
            this.theme = theme != null ? theme : InvoiceTheme.CLASSIC;
            return this;
        }

        public Builder format(String formatStr) {
            this.format = PrintFormat.fromString(formatStr);
            return this;
        }

        public Builder format(PrintFormat format) {
            this.format = format != null ? format : PrintFormat.A4;
            return this;
        }

        public Builder themeColor(String colorStr) {
            this.colorPalette = InvoiceColorPalette.resolve(colorStr);
            return this;
        }

        public Builder colorPalette(InvoiceColorPalette colorPalette) {
            this.colorPalette = colorPalette != null ? colorPalette : InvoiceColorPalette.CLASSIC_BLUE;
            return this;
        }

        public InvoicePrintOptions build() {
            return new InvoicePrintOptions(theme, format, colorPalette);
        }
    }

    public static InvoicePrintOptions resolve(String sizeParam, String formatParam, String themeParam, String colorParam, FirmDetails firm) {
        PrintFormat format;
        if (formatParam != null && !formatParam.isBlank()) {
            format = PrintFormat.fromString(formatParam);
        } else if (sizeParam != null && !sizeParam.isBlank()) {
            format = PrintFormat.fromString(sizeParam);
        } else if (firm != null && firm.getInvoicePrintFormat() != null && !firm.getInvoicePrintFormat().isBlank()) {
            format = PrintFormat.fromString(firm.getInvoicePrintFormat());
        } else {
            format = PrintFormat.A4;
        }

        InvoiceTheme theme;
        if (themeParam != null && !themeParam.isBlank()) {
            theme = InvoiceTheme.fromString(themeParam);
        } else if (firm != null && firm.getInvoicePrintTheme() != null && !firm.getInvoicePrintTheme().isBlank()) {
            theme = InvoiceTheme.fromString(firm.getInvoicePrintTheme());
        } else {
            theme = InvoiceTheme.CLASSIC;
        }

        InvoiceColorPalette color;
        if (colorParam != null && !colorParam.isBlank()) {
            color = InvoiceColorPalette.resolve(colorParam);
        } else if (firm != null && firm.getInvoicePrintThemeColor() != null && !firm.getInvoicePrintThemeColor().isBlank()) {
            color = InvoiceColorPalette.resolve(firm.getInvoicePrintThemeColor());
        } else {
            color = InvoiceColorPalette.CLASSIC_BLUE;
        }

        return new InvoicePrintOptions(theme, format, color);
    }
}
