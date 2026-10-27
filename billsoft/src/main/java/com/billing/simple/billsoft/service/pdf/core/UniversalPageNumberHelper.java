package com.billing.simple.billsoft.service.pdf.core;

import java.awt.Color;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

public class UniversalPageNumberHelper extends PdfPageEventHelper {

    private final float fontSize;
    private final float yOffset;
    private PdfTemplate totalTemplate;

    public UniversalPageNumberHelper() {
        this(8f, 16f);
    }

    public UniversalPageNumberHelper(float fontSize) {
        this(fontSize, 16f);
    }

    public UniversalPageNumberHelper(float fontSize, float yOffset) {
        this.fontSize = fontSize;
        this.yOffset = yOffset;
    }

    @Override
    public void onOpenDocument(PdfWriter writer, Document document) {
        totalTemplate = writer.getDirectContent().createTemplate(30, 16);
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        int pageNum = writer.getPageNumber();
        String text = "Page " + pageNum + " of ";
        Font font = new Font(Font.HELVETICA, fontSize, Font.NORMAL, new Color(107, 114, 128));

        float x = (document.right() + document.left()) / 2f;
        float y = document.bottom() - yOffset;

        ColumnText.showTextAligned(
                writer.getDirectContent(),
                Element.ALIGN_RIGHT,
                new Phrase(text, font),
                x,
                y,
                0
        );

        writer.getDirectContent().addTemplate(totalTemplate, x, y);
    }

    @Override
    public void onCloseDocument(PdfWriter writer, Document document) {
        if (totalTemplate != null) {
            totalTemplate.beginText();
            Font font = new Font(Font.HELVETICA, fontSize, Font.NORMAL, new Color(107, 114, 128));
            try {
                totalTemplate.setFontAndSize(font.getBaseFont() != null ? font.getBaseFont() : com.lowagie.text.pdf.BaseFont.createFont(), fontSize);
                totalTemplate.showText(String.valueOf(writer.getPageNumber() - 1));
            } catch (Exception ignored) {
            } finally {
                totalTemplate.endText();
            }
        }
    }
}
